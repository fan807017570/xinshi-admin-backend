package com.xinshi.admin.interfaces.web.h5;

import com.xinshi.admin.application.h5.H5QueryContextService;
import java.io.IOException;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Service-signed endpoint used only by xinshi-rag. */
@RestController
public class InternalWechatController {
    private final H5QueryContextService contextService;

    public InternalWechatController(H5QueryContextService contextService) { this.contextService = contextService; }

    @PostMapping("/api/internal/wechat/score-query-contexts")
    public Map<String, Object> store(
            HttpServletRequest request,
            @RequestHeader("X-Service-Id") String serviceId,
            @RequestHeader("X-Timestamp") String timestamp,
            @RequestHeader("X-Nonce") String nonce,
            @RequestHeader("X-Signature") String signature) throws IOException {
        return H5Responses.success(contextService.storeInternal(StreamUtils.copyToByteArray(request.getInputStream()), serviceId, timestamp, nonce, signature));
    }
}
