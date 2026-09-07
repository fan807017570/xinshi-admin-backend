package com.xinshi.admin.interfaces.dto.h5;

import javax.validation.constraints.NotBlank;

/**
 * Login confirmation payload.
 *
 * @author Codex
 * @date 2026-08-31
 */
public class H5LoginConfirmationRequest {
    @NotBlank
    private String loginConfirmationToken;

    public String getLoginConfirmationToken() {
        return loginConfirmationToken;
    }

    public void setLoginConfirmationToken(String loginConfirmationToken) {
        this.loginConfirmationToken = loginConfirmationToken;
    }

    @Override
    public String toString() {
        return "H5LoginConfirmationRequest{loginConfirmationToken='***'}";
    }
}
