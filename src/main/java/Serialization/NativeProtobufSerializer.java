package Serialization;

import com.google.protobuf.Message;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NativeProtobufSerializer implements Serializer {

    // 缓存 parseFrom 方法，避免每次反射带来的性能损耗
    // Key: 类对象, Value: 该类的 parseFrom 方法
    private static final Map<Class<?>, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    @Override
    public byte[] serialize(Object obj) {
        // 关键点：原生 Protobuf 只能序列化它自己生成的类
        // 所以这里必须检查 obj 是否是 Message 的实例
        if (!(obj instanceof Message)) {
            throw new IllegalArgumentException("该对象不是 Protobuf 生成的类，无法使用原生 Protobuf 序列化: " + obj.getClass().getName());
        }

        // 调用 Protobuf 生成类的 toByteArray() 方法
        return ((Message) obj).toByteArray();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try {
            Method method = getParseFromMethod(clazz);
            return (T) method.invoke(null, (Object) bytes);
        } catch (Exception e) {
            throw new RuntimeException("Protobuf 反序列化失败", e);
        }
    }

    @Override
    public byte getCode() {
        return SerializerCode.Proto_SERIALIZER_Google.getCode(); // 假设 3 代表 Native Protobuf
    }

    private Method getParseFromMethod(Class<?> clazz) {
        return METHOD_CACHE.computeIfAbsent(clazz, c -> {
            try {
                // Protobuf 生成的类都有一个 static parseFrom(byte[]) 方法
                Method method = c.getMethod("parseFrom", byte[].class);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("未找到 parseFrom 方法，请检查是否为 Protobuf 生成类: " + c.getName());
            }
        });
    }
}