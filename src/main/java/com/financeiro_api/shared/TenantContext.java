package com.financeiro_api.shared;

import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<UUID> ENTERPRISE = new ThreadLocal<>();
    private static final ThreadLocal<UUID> USER = new ThreadLocal<>();
    private static final ThreadLocal<String> EMAIL = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID enterpriseId) {
        ENTERPRISE.set(enterpriseId);
    }

    public static UUID get() {
        return ENTERPRISE.get();
    }

    public static void setUserId(UUID userId) {
        USER.set(userId);
    }

    public static UUID getUserId() {
        return USER.get();
    }

    public static void setEmail(String email) {
        EMAIL.set(email);
    }

    public static String getEmail() {
        return EMAIL.get();
    }

    public static void clear() {
        ENTERPRISE.remove();
        USER.remove();
        EMAIL.remove();
    }
}
