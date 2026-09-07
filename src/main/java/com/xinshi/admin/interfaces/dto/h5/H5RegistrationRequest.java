package com.xinshi.admin.interfaces.dto.h5;

import javax.validation.constraints.NotBlank;

/**
 * Atomic parent registration and student binding payload.
 *
 * @author Codex
 * @date 2026-08-31
 */
public class H5RegistrationRequest {
    @NotBlank
    private String studentName;
    @NotBlank
    private String mobile;
    @NotBlank
    private String verificationCode;
    @NotBlank
    private String studentNo;
    @NotBlank
    private String idCardLast4;
    @NotBlank
    private String agreementVersion;

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    public String getStudentNo() {
        return studentNo;
    }

    public void setStudentNo(String studentNo) {
        this.studentNo = studentNo;
    }

    public String getIdCardLast4() {
        return idCardLast4;
    }

    public void setIdCardLast4(String idCardLast4) {
        this.idCardLast4 = idCardLast4;
    }

    public String getAgreementVersion() {
        return agreementVersion;
    }

    public void setAgreementVersion(String agreementVersion) {
        this.agreementVersion = agreementVersion;
    }

    @Override
    public String toString() {
        return "H5RegistrationRequest{studentName='***', mobile='***', verificationCode='***', "
                + "studentNo='***', idCardLast4='***', agreementVersion='" + agreementVersion + "'}";
    }
}
