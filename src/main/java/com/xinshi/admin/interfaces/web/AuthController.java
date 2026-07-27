/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.PutMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestHeader
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.bind.annotation.RestController
 */
package com.xinshi.admin.interfaces.web;

import com.xinshi.admin.application.auth.AuthApplicationService;
import com.xinshi.admin.application.auth.command.LoginCommand;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/auth"})
public class AuthController {
    private final AuthApplicationService authApplicationService;

    public AuthController(AuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    @PostMapping(value={"/login"})
    public Map<String, Object> login(@RequestBody Map<String, Object> request) {
        String loginName = String.valueOf(request.get("loginName"));
        String password = String.valueOf(request.getOrDefault("password", ""));
        return this.authApplicationService.login(new LoginCommand(loginName, password));
    }

    @GetMapping(value={"/me"})
    public Map<String, Object> me(@RequestHeader(value="Authorization", required=false) String authorization, @RequestHeader(value="X-Auth-Token", required=false) String token, @RequestParam(required=false) String loginName) {
        return this.authApplicationService.currentUser(this.resolveToken(authorization, token));
    }

    @PostMapping(value={"/logout"})
    public Map<String, Object> logout(@RequestHeader(value="Authorization", required=false) String authorization, @RequestHeader(value="X-Auth-Token", required=false) String token) {
        this.authApplicationService.logout(this.resolveToken(authorization, token));
        return new HashMap<String, Object>();
    }

    @PutMapping(value={"/profile"})
    public Map<String, Object> updateProfile(@RequestBody Map<String, Object> request) {
        return this.authApplicationService.updateProfile(request);
    }

    private String resolveToken(String authorization, String token) {
        String candidate = token;
        if (candidate == null || candidate.trim().isEmpty()) {
            candidate = authorization;
        }
        if (candidate == null) {
            return null;
        }
        if ((candidate = candidate.trim()).toLowerCase().startsWith("bearer ")) {
            candidate = candidate.substring(7).trim();
        }
        return candidate;
    }
}

