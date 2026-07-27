/*
 * Decompiled with CFR 0.152.
 */
package com.xinshi.admin.domain.achievement;

import com.xinshi.admin.domain.achievement.StudentAchievement;
import java.util.List;

public interface AchievementRepository {
    public List<StudentAchievement> findByTermAndStudent(long var1, long var3);

    public StudentAchievement save(StudentAchievement var1);

    public void delete(long var1);

    public void update(StudentAchievement var1);
}

