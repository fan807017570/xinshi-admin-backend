/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.annotation.Value
 *  org.springframework.http.HttpEntity
 *  org.springframework.http.HttpHeaders
 *  org.springframework.http.HttpMethod
 *  org.springframework.http.MediaType
 *  org.springframework.http.ResponseEntity
 *  org.springframework.http.client.ClientHttpRequestFactory
 *  org.springframework.http.client.SimpleClientHttpRequestFactory
 *  org.springframework.stereotype.Service
 *  org.springframework.util.MultiValueMap
 *  org.springframework.util.StringUtils
 *  org.springframework.web.client.RestClientException
 *  org.springframework.web.client.RestTemplate
 */
package com.xinshi.admin.application.commentpolish;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class CommentPolishService {
    private static final Logger log = LoggerFactory.getLogger(CommentPolishService.class);
    private static final String SYSTEM_PROMPT = "你是一位经验丰富的中学老师，擅长撰写学生评语。你的任务是帮助润色老师写的学生评语。\n请遵循以下原则：\n1. 语言自然朴实，用词简单，像老师在说话一样\n2. 保持老师的口吻，亲切但不失专业性\n3. 保持原意不变，不要添加原文没有的内容\n4. 字数控制在适当范围，不要过于冗长\n5. 直接返回润色后的文本，不要加任何解释或前缀";
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final RestTemplate restTemplate;

    public CommentPolishService(@Value(value="${xinshi.deepseek.api-key:}") String apiKey, @Value(value="${xinshi.deepseek.base-url:https://api.deepseek.com}") String baseUrl, @Value(value="${xinshi.deepseek.model:deepseek-chat}") String model, @Value(value="${xinshi.deepseek.timeout-seconds:30}") int timeoutSeconds) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int)Duration.ofSeconds(timeoutSeconds).toMillis());
        factory.setReadTimeout((int)Duration.ofSeconds(timeoutSeconds).toMillis());
        this.restTemplate = new RestTemplate((ClientHttpRequestFactory)factory);
    }

    public String polish(String text) {
        if (!StringUtils.hasText((String)this.apiKey)) {
            throw new IllegalStateException("AI 润色服务未配置，请联系管理员");
        }
        String trimmedText = text.trim();
        if (!StringUtils.hasText((String)trimmedText)) {
            throw new IllegalArgumentException("评语文本不能为空");
        }
        Map<String, Object> requestBody = this.buildRequestBody(trimmedText);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(this.apiKey);
        String url = this.baseUrl + "/v1/chat/completions";
        log.debug("调用 DeepSeek API 润色评语, url={}, textLength={}", (Object)url, (Object)trimmedText.length());
        try {
            HttpEntity entity = new HttpEntity(requestBody, (MultiValueMap)headers);
            ResponseEntity response = this.restTemplate.exchange(url, HttpMethod.POST, entity, Map.class, new Object[0]);
            if (response.getBody() == null) {
                log.error("DeepSeek API 返回空响应");
                throw new IllegalStateException("AI 润色失败，请稍后再试");
            }
            String polished = this.extractContent((Map)response.getBody());
            log.debug("评语润色完成, originalLength={}, polishedLength={}", (Object)trimmedText.length(), (Object)(polished != null ? polished.length() : 0));
            return polished;
        }
        catch (RestClientException e) {
            log.error("调用 DeepSeek API 失败: {}", (Object)e.getMessage(), (Object)e);
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                throw new IllegalStateException("AI 服务响应超时，请稍后再试", e);
            }
            throw new IllegalStateException("AI 润色失败，请稍后再试", e);
        }
    }

    private Map<String, Object> buildRequestBody(String userText) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", this.model);
        body.put("temperature", 0.7);
        body.put("max_tokens", 1000);
        ArrayList messages = new ArrayList();
        LinkedHashMap<String, String> systemMsg = new LinkedHashMap<String, String>();
        systemMsg.put("role", "system");
        systemMsg.put("content", SYSTEM_PROMPT);
        messages.add(systemMsg);
        LinkedHashMap<String, String> userMsg = new LinkedHashMap<String, String>();
        userMsg.put("role", "user");
        userMsg.put("content", userText);
        messages.add(userMsg);
        body.put("messages", messages);
        return body;
    }

    private String extractContent(Map<String, Object> responseBody) {
        List choices = (List)responseBody.get("choices");
        if (choices == null || choices.isEmpty()) {
            log.error("DeepSeek API 响应中没有 choices 字段: {}", responseBody);
            throw new IllegalStateException("AI 润色失败，响应格式异常");
        }
        Map firstChoice = (Map)choices.get(0);
        Map message = (Map)firstChoice.get("message");
        if (message == null) {
            log.error("DeepSeek API 响应中没有 message 字段: {}", (Object)firstChoice);
            throw new IllegalStateException("AI 润色失败，响应格式异常");
        }
        String content = (String)message.get("content");
        if (content == null) {
            log.error("DeepSeek API 响应中没有 content 字段: {}", (Object)message);
            throw new IllegalStateException("AI 润色失败，响应格式异常");
        }
        return content.trim();
    }
}

