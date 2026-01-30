package org.AI.panda.auth.web;

import jakarta.servlet.http.HttpServletRequest;

public final class UserIdResolver {
    public static final String ATTR_SHARE_OWNER_USER_ID = "SHARE_OWNER_USER_ID";
    public static final String ATTR_SHARE_SESSION_ID = "SHARE_SESSION_ID";

    private UserIdResolver() {
    }

    public static String resolve(HttpServletRequest request, String fallbackUserId) {
        if (request != null) {
            Object shareOwner = request.getAttribute(ATTR_SHARE_OWNER_USER_ID);
            if (shareOwner instanceof String s && !s.isBlank()) {
                return s;
            }
            Object v = request.getAttribute(AuthUserIdFilter.ATTR_AUTH_USER_ID);
            if (v instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        if (fallbackUserId != null && !fallbackUserId.isBlank()) {
            return fallbackUserId;
        }
        return "default-user";
    }

    public static boolean isVisitor(HttpServletRequest request) {
        if (request == null) return false;
        Object shareOwner = request.getAttribute(ATTR_SHARE_OWNER_USER_ID);
        if (!(shareOwner instanceof String ownerId) || ownerId.isBlank()) return false;
        Object auth = request.getAttribute(AuthUserIdFilter.ATTR_AUTH_USER_ID);
        if (auth instanceof String authUserId && !authUserId.isBlank()) {
            return !ownerId.equals(authUserId);
        }
        return true;
    }

    public static String resolveSharedSessionId(HttpServletRequest request) {
        if (request == null) return null;
        Object v = request.getAttribute(ATTR_SHARE_SESSION_ID);
        if (v instanceof String s && !s.isBlank()) return s;
        return null;
    }
}
