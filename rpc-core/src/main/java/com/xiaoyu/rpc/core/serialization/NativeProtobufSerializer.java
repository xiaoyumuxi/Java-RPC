package com.xiaoyu.rpc.core.serialization;

import com.google.protobuf.*;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 增强版 Protobuf 序列化器
 * 支持 Protobuf Message 以及基本类型的自动包装
 */
public class NativeProtobufSerializer implements Serializer {

    private static final Map<Class<?>, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    @Override
    public byte[] serialize(Object obj) {
        if (obj instanceof Message) {
            return ((Message) obj).toByteArray();
        }
        // 基本类型包装
        if (obj instanceof String) {
            return StringValue.of((String) obj).toByteArray();
        }
        if (obj instanceof Integer) {
            return Int32Value.of((Integer) obj).toByteArray();
        }
        if (obj instanceof Long) {
            return Int64Value.of((Long) obj).toByteArray();
        }
        if (obj instanceof Double) {
            return DoubleValue.of((Double) obj).toByteArray();
        }
        if (obj instanceof Float) {
            return FloatValue.of((Float) obj).toByteArray();
        }
        if (obj instanceof Boolean) {
            return BoolValue.of((Boolean) obj).toByteArray();
        }

        throw new IllegalArgumentException("不支持的类型，请使用 Protobuf Message 或基本类型: " + obj.getClass().getName());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try {
            // 基本类型解包
            if (clazz == String.class) {
                return (T) StringValue.parseFrom(bytes).getValue();
            }
            if (clazz == Integer.class || clazz == int.class) {
                return (T) Integer.valueOf(Int32Value.parseFrom(bytes).getValue());
            }
            if (clazz == Long.class || clazz == long.class) {
                return (T) Long.valueOf(Int64Value.parseFrom(bytes).getValue());
            }
            if (clazz == Double.class || clazz == double.class) {
                return (T) Double.valueOf(DoubleValue.parseFrom(bytes).getValue());
            }
            if (clazz == Float.class || clazz == float.class) {
                return (T) Float.valueOf(FloatValue.parseFrom(bytes).getValue());
            }
            if (clazz == Boolean.class || clazz == boolean.class) {
                return (T) Boolean.valueOf(BoolValue.parseFrom(bytes).getValue());
            }

            // Protobuf Message 处理
            Method method = getParseFromMethod(clazz);
            return (T) method.invoke(null, (Object) bytes);
        } catch (Exception e) {
            throw new RuntimeException("Protobuf 反序列化失败: " + clazz.getName(), e);
        }
    }

    @Override
    public byte getCode() {
        return SerializerCode.PROTOBUF_SERIALIZER;
    }

    private Method getParseFromMethod(Class<?> clazz) {
        return METHOD_CACHE.computeIfAbsent(clazz, c -> {
            try {
                Method method = c.getMethod("parseFrom", byte[].class);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("未找到 parseFrom 方法，请检查是否为 Protobuf 生成类: " + c.getName());
            }
        });
    }
}