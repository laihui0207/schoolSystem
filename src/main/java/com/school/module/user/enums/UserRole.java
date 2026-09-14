package com.school.module.user.enums;

public enum UserRole {
    SUPER_ADMIN(1, "super_admin", "ROLE_SUPER_ADMIN"),
    SCHOOL_ADMIN(2, "admin", "ROLE_SCHOOL_ADMIN"),
    TEACHER(3, "teacher", "ROLE_TEACHER"),
    STUDENT(4, "student", "ROLE_STUDENT"),
    PARENT(5, "parent", "ROLE_PARENT");

    public final int code;
    public final String role;
    public final String authority;

    UserRole(int code, String role, String authority) {
        this.code = code;
        this.role = role;
        this.authority = authority;
    }

    public static UserRole fromCode(int code) {
        for (UserRole r : values()) {
            if (r.code == code) return r;
        }
        throw new IllegalArgumentException("Unknown user type: " + code);
    }

    public int getUserType() { return code; }
}
