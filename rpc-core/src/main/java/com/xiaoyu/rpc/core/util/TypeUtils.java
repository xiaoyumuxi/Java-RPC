package com.xiaoyu.rpc.core.util;

import java.util.Map;

/**
 * RPC 反射调用相关的类型工具。
 * <p>
 * Java 基本类型（如 int、boolean）不能直接通过 Class.forName("int") 解析，
 * 同时多数序列化器也需要使用对应的包装类型进行反序列化。
 */
public final class TypeUtils {

    private static final Map<String, Class<?>> PRIMITIVE_TYPES = Map.ofEntries(
            Map.entry("boolean", boolean.class),
            Map.entry("byte", byte.class),
            Map.entry("short", short.class),
            Map.entry("int", int.class),
            Map.entry("long", long.class),
            Map.entry("float", float.class),
            Map.entry("double", double.class),
            Map.entry("char", char.class),
            Map.entry("void", void.class));

    private static final Map<Class<?>, Class<?>> WRAPPER_TYPES = Map.ofEntries(
            Map.entry(boolean.class, Boolean.class),
            Map.entry(byte.class, Byte.class),
            Map.entry(short.class, Short.class),
            Map.entry(int.class, Integer.class),
            Map.entry(long.class, Long.class),
            Map.entry(float.class, Float.class),
            Map.entry(double.class, Double.class),
            Map.entry(char.class, Character.class),
            Map.entry(void.class, Void.class));

    private TypeUtils() {
    }

    /**
     * 按 JVM/Java 类型名解析 Class，兼容基本类型名称。
     */
    public static Class<?> resolveClass(String typeName) throws ClassNotFoundException {
        Class<?> primitiveType = PRIMITIVE_TYPES.get(typeName);
        return primitiveType != null ? primitiveType : Class.forName(typeName);
    }

    /**
     * 将基本类型转换成包装类型，普通引用类型保持不变。
     */
    public static Class<?> wrapPrimitive(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return type;
        }
        return WRAPPER_TYPES.get(type);
    }
}
