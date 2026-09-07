package com.xinshi.admin.infrastructure.security;

/**
 * Request-scoped H5 session identity populated by {@link H5AuthInterceptor}.
 *
 * @author Codex
 * @date 2026-08-31
 */
public final class H5RequestContext {
    private static final ThreadLocal<SessionIdentity> CURRENT = new ThreadLocal<>();

    private H5RequestContext() {
    }

    public static void set(SessionIdentity identity) {
        CURRENT.set(identity);
    }

    public static SessionIdentity require() {
        SessionIdentity identity = CURRENT.get();
        if (identity == null) {
            throw new IllegalStateException("H5 request context is unavailable");
        }
        return identity;
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Immutable H5 session identity.
     *
     * @author Codex
     * @date 2026-08-31
     */
    public static final class SessionIdentity {
        private final Long sessionId;
        private final Long wechatAccountId;
        private final Long parentUserId;
        private final String appid;
        private final String csrfTokenHash;

        public SessionIdentity(
                Long sessionId,
                Long wechatAccountId,
                Long parentUserId,
                String appid,
                String csrfTokenHash) {
            this.sessionId = sessionId;
            this.wechatAccountId = wechatAccountId;
            this.parentUserId = parentUserId;
            this.appid = appid;
            this.csrfTokenHash = csrfTokenHash;
        }

        public Long getSessionId() {
            return sessionId;
        }

        public Long getWechatAccountId() {
            return wechatAccountId;
        }

        public Long getParentUserId() {
            return parentUserId;
        }

        public String getAppid() {
            return appid;
        }

        public String getCsrfTokenHash() {
            return csrfTokenHash;
        }
    }
}
