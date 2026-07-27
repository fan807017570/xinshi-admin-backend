/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.jdbc.support.GeneratedKeyHolder
 *  org.springframework.jdbc.support.KeyHolder
 *  org.springframework.util.StringUtils
 */
package com.xinshi.admin.application.school;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.util.StringUtils;

public abstract class SchoolBaseService {
    protected final JdbcTemplate jdbcTemplate;

    protected SchoolBaseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    protected long count(String table, String whereClause) {
        Long value = (Long)this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + table + " WHERE " + whereClause, Long.class);
        return value == null ? 0L : value;
    }

    protected int exists(String sql, Object ... args) {
        Integer value = (Integer)this.jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    protected long insert(String table, String sql, Object ... args) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        this.jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, 1);
            for (int i = 0; i < args.length; ++i) {
                Object arg = args[i];
                int index = i + 1;
                if (arg == null) {
                    ps.setObject(index, null);
                    continue;
                }
                if (arg instanceof Integer) {
                    ps.setInt(index, (Integer)arg);
                    continue;
                }
                if (arg instanceof Long) {
                    ps.setLong(index, (Long)arg);
                    continue;
                }
                if (arg instanceof Double) {
                    ps.setDouble(index, (Double)arg);
                    continue;
                }
                if (arg instanceof LocalDate) {
                    ps.setDate(index, Date.valueOf((LocalDate)arg));
                    continue;
                }
                if (arg instanceof Timestamp) {
                    ps.setTimestamp(index, (Timestamp)arg);
                    continue;
                }
                ps.setObject(index, arg);
            }
            return ps;
        }, (KeyHolder)keyHolder);
        Number generatedKey = keyHolder.getKey();
        if (generatedKey == null) {
            throw new IllegalStateException("Insert failed for table " + table);
        }
        return generatedKey.longValue();
    }

    protected Map<String, Object> first(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        return new LinkedHashMap<String, Object>(list.get(0));
    }

    protected String requiredString(Map<String, Object> request, String key) {
        String value = this.optionalString(request, key, null);
        if (!StringUtils.hasText((String)value)) {
            throw new IllegalArgumentException(key + " 不能为空");
        }
        return value.trim();
    }

    protected String optionalString(Map<String, Object> request, String key, String defaultValue) {
        Object value = request.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    protected Integer requiredInteger(Map<String, Object> request, String key) {
        Integer value = this.optionalInteger(request, key, null);
        if (value == null) {
            throw new IllegalArgumentException(key + " 不能为空");
        }
        return value;
    }

    protected Integer optionalInteger(Map<String, Object> request, String key, Integer defaultValue) {
        Object value = request.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number)value).intValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText((String)text)) {
            return defaultValue;
        }
        return Integer.valueOf(text);
    }

    protected Long requiredLong(Map<String, Object> request, String key) {
        Long value = this.optionalLong(request, key);
        if (value == null) {
            throw new IllegalArgumentException(key + " 不能为空");
        }
        return value;
    }

    protected Long optionalLong(Map<String, Object> request, String key) {
        Object value = request.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number)value).longValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText((String)text)) {
            return null;
        }
        return Long.valueOf(text);
    }

    protected Double requiredDouble(Map<String, Object> request, String key) {
        Object value = request.get(key);
        if (value == null) {
            throw new IllegalArgumentException(key + " 不能为空");
        }
        if (value instanceof Number) {
            return ((Number)value).doubleValue();
        }
        return Double.valueOf(String.valueOf(value));
    }

    protected Double optionalDouble(Map<String, Object> request, String key, Double defaultValue) {
        Object value = request.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number)value).doubleValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText((String)text)) {
            return defaultValue;
        }
        return Double.valueOf(text);
    }

    protected LocalDate optionalLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate) {
            return (LocalDate)value;
        }
        if (value instanceof Date) {
            return ((Date)value).toLocalDate();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText((String)text)) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        }
        catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("日期格式错误: " + text);
        }
    }

    protected List<Map<String, Object>> mapList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof List) {
            return (List)value;
        }
        return Collections.emptyList();
    }

    protected List<String> stringList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof List) {
            List<?> raw = (List<?>)value;
            return raw.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText((String)text)) {
            return Collections.emptyList();
        }
        String[] parts = text.split(",");
        ArrayList<String> list = new ArrayList<String>();
        for (String part : parts) {
            if (!StringUtils.hasText((String)part)) continue;
            list.add(part.trim());
        }
        return list;
    }

    protected String safeText(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }

    protected String fmtPdfDate(LocalDateTime dateTime) {
        return dateTime == null ? "" : String.format("%d-%02d-%02d %02d:%02d", dateTime.getYear(), dateTime.getMonthValue(), dateTime.getDayOfMonth(), dateTime.getHour(), dateTime.getMinute());
    }
}

