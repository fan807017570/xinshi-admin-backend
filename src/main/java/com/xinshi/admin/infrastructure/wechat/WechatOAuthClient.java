package com.xinshi.admin.infrastructure.wechat;

import com.xinshi.admin.interfaces.web.h5.H5ApiException;
import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Minimal WeChat OAuth client with an explicit local mock mode.
 *
 * @author Codex
 * @date 2026-08-31
 */
@Component
public class WechatOAuthClient {
    private final RestTemplate restTemplate;
    private final boolean mockEnabled;
    private final String mockOpenid;
    private final String mockNickname;

    public WechatOAuthClient(
            RestTemplateBuilder builder,
            @Value("${h5.wechat.mock-enabled:false}") boolean mockEnabled,
            @Value("${h5.wechat.mock-openid:mock-parent-openid}") String mockOpenid,
            @Value("${h5.wechat.mock-nickname:微信家长}") String mockNickname) {
        this.restTemplate = builder.setConnectTimeout(java.time.Duration.ofSeconds(3))
                .setReadTimeout(java.time.Duration.ofSeconds(5))
                .build();
        this.mockEnabled = mockEnabled;
        this.mockOpenid = mockOpenid;
        this.mockNickname = mockNickname;
    }

    public WechatIdentity exchange(String appid, String appSecretReference, String code) {
        if (mockEnabled) {
            return new WechatIdentity(mockOpenid, mockNickname);
        }
        String appSecret = resolveSecret(appSecretReference);
        URI tokenUri = UriComponentsBuilder
                .fromHttpUrl("https://api.weixin.qq.com/sns/oauth2/access_token")
                .queryParam("appid", appid)
                .queryParam("secret", appSecret)
                .queryParam("code", code)
                .queryParam("grant_type", "authorization_code")
                .build(true)
                .toUri();
        try {
            Map<?, ?> token = restTemplate.getForObject(tokenUri, Map.class);
            if (token == null || token.get("openid") == null || token.get("access_token") == null) {
                throw oauthFailure();
            }
            String openid = String.valueOf(token.get("openid"));
            String accessToken = String.valueOf(token.get("access_token"));
            URI userInfoUri = UriComponentsBuilder
                    .fromHttpUrl("https://api.weixin.qq.com/sns/userinfo")
                    .queryParam("access_token", accessToken)
                    .queryParam("openid", openid)
                    .queryParam("lang", "zh_CN")
                    .build(true)
                    .toUri();
            Map<?, ?> userInfo = restTemplate.getForObject(userInfoUri, Map.class);
            if (userInfo == null || userInfo.get("nickname") == null) {
                throw new H5ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "WECHAT_USERINFO_FAILED",
                        "微信用户信息读取失败，请稍后重试");
            }
            return new WechatIdentity(openid, String.valueOf(userInfo.get("nickname")));
        } catch (RestClientException exception) {
            throw oauthFailure();
        }
    }

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    private String resolveSecret(String reference) {
        if (!StringUtils.hasText(reference)) {
            throw oauthFailure();
        }
        String environmentName = reference.startsWith("env:") ? reference.substring(4) : reference;
        String secret = System.getenv(environmentName);
        if (!StringUtils.hasText(secret)) {
            throw oauthFailure();
        }
        return secret;
    }

    private H5ApiException oauthFailure() {
        return new H5ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "WECHAT_OAUTH_FAILED",
                "微信身份验证失败，请稍后重试");
    }

    /**
     * Trusted identity returned by the WeChat OAuth API.
     *
     * @author Codex
     * @date 2026-08-31
     */
    public static final class WechatIdentity {
        private final String openid;
        private final String nickname;

        public WechatIdentity(String openid, String nickname) {
            this.openid = openid;
            this.nickname = nickname;
        }

        public String getOpenid() {
            return openid;
        }

        public String getNickname() {
            return nickname;
        }
    }
}
