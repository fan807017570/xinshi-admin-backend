package com.xinshi.admin.interfaces.dto.h5;

import javax.validation.constraints.NotBlank;

/**
 * Mobile verification code request.
 *
 * @author Codex
 * @date 2026-08-31
 */
public class H5MobileVerificationRequest {
    @NotBlank
    private String mobile;

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    @Override
    public String toString() {
        return "H5MobileVerificationRequest{mobile='***'}";
    }
}
