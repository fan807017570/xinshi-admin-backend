/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.courseresult;

public enum ResultStatus {
    DRAFT(1),
    PUBLISHED(2);

    private final int code;

    private ResultStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return this.code;
    }

    public static ResultStatus fromCode(int code) {
        switch (code) {
            case 1: {
                return DRAFT;
            }
            case 2: {
                return PUBLISHED;
            }
        }
        return DRAFT;
    }
}

