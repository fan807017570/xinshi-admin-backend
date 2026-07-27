/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.studentparent;

public class StudentParent {
    private final long parentUserId;
    private final String relationType;
    private final int isPrimary;

    private StudentParent(long parentUserId, String relationType, int isPrimary) {
        this.parentUserId = parentUserId;
        this.relationType = relationType;
        this.isPrimary = isPrimary;
    }

    public static StudentParent create(long parentUserId, String relationType, int isPrimary) {
        return new StudentParent(parentUserId, relationType, isPrimary);
    }

    public static StudentParent rehydrate(long parentUserId, String relationType, int isPrimary) {
        return new StudentParent(parentUserId, relationType, isPrimary);
    }

    public long getParentUserId() {
        return this.parentUserId;
    }

    public String getRelationType() {
        return this.relationType;
    }

    public int getIsPrimary() {
        return this.isPrimary;
    }

    public boolean isPrimary() {
        return this.isPrimary == 1;
    }
}

