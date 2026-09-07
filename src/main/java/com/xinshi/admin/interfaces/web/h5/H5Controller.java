package com.xinshi.admin.interfaces.web.h5;

import com.xinshi.admin.application.h5.H5AuthService;
import com.xinshi.admin.application.h5.H5QueryContextService;
import com.xinshi.admin.application.h5.H5StudentQueryService;
import com.xinshi.admin.application.h5.H5RegistrationService;
import com.xinshi.admin.infrastructure.security.H5AuthInterceptor;
import com.xinshi.admin.infrastructure.security.H5RequestContext;
import com.xinshi.admin.interfaces.dto.h5.H5LoginConfirmationRequest;
import com.xinshi.admin.interfaces.dto.h5.H5MobileVerificationRequest;
import com.xinshi.admin.interfaces.dto.h5.H5RegistrationRequest;
import com.xinshi.admin.interfaces.dto.h5.H5QueryContextResolveRequest;
import java.util.Map;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public H5 API using the independent H5 Cookie session. */
@RestController
@RequestMapping("/api/h5")
public class H5Controller {
    private final H5AuthService authService;
    private final H5QueryContextService contextService;
    private final H5StudentQueryService queryService;
    private final H5RegistrationService registrationService;

    public H5Controller(H5AuthService authService, H5QueryContextService contextService, H5StudentQueryService queryService, H5RegistrationService registrationService) {
        this.authService = authService;
        this.contextService = contextService;
        this.queryService = queryService;
        this.registrationService = registrationService;
    }

    @GetMapping("/bootstrap")
    public Map<String, Object> bootstrap(@RequestParam String appid, HttpServletRequest request, HttpServletResponse response) {
        return H5Responses.success(authService.bootstrap(appid, cookie(request, H5AuthInterceptor.SESSION_COOKIE), response));
    }

    @PostMapping("/wechat/login-confirmations")
    public Map<String, Object> confirm(@Valid @RequestBody H5LoginConfirmationRequest body, HttpServletRequest request) {
        return H5Responses.success(authService.confirmLogin(body.getLoginConfirmationToken(), cookie(request, H5AuthService.PREAUTH_COOKIE)));
    }

    @GetMapping("/wechat/callback")
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state, HttpServletResponse response) {
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, authService.callback(code, state, response)).build();
    }

    @PostMapping("/query-context/resolve")
    public Map<String, Object> resolve(@Valid @RequestBody H5QueryContextResolveRequest body) {
        H5RequestContext.SessionIdentity identity = H5RequestContext.require();
        if (identity.getParentUserId() == null) {
            throw new H5ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "REGISTRATION_REQUIRED", "请先完成家长注册和学生绑定");
        }
        return H5Responses.success(contextService.resolveSelection(identity.getWechatAccountId(), identity.getParentUserId(), body.getStudentId(), body.getAcademicTermId(), body.getExamTypeId()));
    }

    @PostMapping("/mobile/verification-codes")
    public Map<String, Object> verification(@Valid @RequestBody H5MobileVerificationRequest body, HttpServletRequest request) {
        return H5Responses.success(registrationService.sendVerification(body.getMobile(), request.getRemoteAddr()));
    }

    @PostMapping("/registrations")
    public Map<String, Object> register(@Valid @RequestBody H5RegistrationRequest body, HttpServletResponse response) {
        H5RegistrationService.RegistrationResult result = registrationService.register(body);
        authService.replaceSessionCookie(response, result.getSessionToken());
        return H5Responses.success(result.getData());
    }

    @GetMapping("/students") public Map<String, Object> students() { return H5Responses.success(queryService.students()); }
    @GetMapping("/students/{id}/terms") public Map<String, Object> terms(@PathVariable long id) { return H5Responses.success(queryService.terms(id)); }
    @GetMapping("/students/{id}/scores") public Map<String, Object> scores(@PathVariable long id, @RequestParam long academicTermId, @RequestParam long examTypeId) { return H5Responses.success(queryService.scores(id, academicTermId, examTypeId)); }
    @GetMapping("/results/{id}") public Map<String, Object> result(@PathVariable long id) { return H5Responses.success(queryService.result(id)); }
    @GetMapping("/students/{id}/teacher-comments") public Map<String, Object> comment(@PathVariable long id, @RequestParam long academicTermId) { return H5Responses.success(queryService.teacherComment(id, academicTermId)); }
    @GetMapping("/students/{id}/honors") public Map<String, Object> honors(@PathVariable long id, @RequestParam long academicTermId, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) { return H5Responses.success(queryService.honors(id, academicTermId, page, pageSize)); }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletResponse response) {
        authService.logout(H5RequestContext.require().getSessionId(), response);
        return H5Responses.success(null);
    }

    private String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
}
