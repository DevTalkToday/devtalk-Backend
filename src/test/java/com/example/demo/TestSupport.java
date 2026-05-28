package com.example.demo;

import java.lang.reflect.Field;

public final class TestSupport {
    private TestSupport() {
    }

    public static <T> T withId(T target, Long id) {
        setField(target, "id", id);
        return target;
    }

    public static void setField(Object target, String name, Object value) {
        Field field = findField(target.getClass(), name);
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Could not set test field: " + name, error);
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalArgumentException("No field named " + name + " on " + type.getName());
    }
}
