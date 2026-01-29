package com.xiaoyu.rpc.core.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KryoSerializerImpl implements Serializer {
    private static final Logger log = LoggerFactory.getLogger(KryoSerializerImpl.class);

    // ThreadLocal for Kryo instances to ensure thread safety and reuse
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setReferences(true);
        kryo.setRegistrationRequired(false);

        // Register custom serializer for Protobuf objects (RpcRequest, RpcResponse)
        // This avoids using JavaSerializer which is slow and uses efficient Protobuf
        // methods directly.
        com.esotericsoftware.kryo.Serializer<Object> protobufSerializer = new com.esotericsoftware.kryo.Serializer<Object>() {
            @Override
            public void write(Kryo kryo, Output output, Object object) {
                if (object instanceof com.google.protobuf.AbstractMessage) {
                    byte[] bytes = ((com.google.protobuf.AbstractMessage) object).toByteArray();
                    output.writeInt(bytes.length, true);
                    output.writeBytes(bytes);
                } else {
                    // Fallback should not happen if registered correctly, but safe to have
                    throw new RuntimeException(
                            "Unsupported Protobuf message type for custom serializer: " + object.getClass());
                }
            }

            @Override
            public Object read(Kryo kryo, Input input, Class<? extends Object> type) {
                try {
                    int length = input.readInt(true);
                    byte[] bytes = input.readBytes(length);
                    // Use reflection to call static parseFrom(byte[]) method
                    // Caching the method would be even faster but this is already much faster than
                    // Java serialization
                    return type.getMethod("parseFrom", byte[].class).invoke(null, (Object) bytes);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to deserialize protobuf: " + type.getName(), e);
                }
            }
        };

        kryo.register(RpcRequest.class, protobufSerializer);
        kryo.register(RpcResponse.class, protobufSerializer);

        // Standard registrations
        kryo.register(String.class);
        kryo.register(Object[].class);
        kryo.register(Class[].class);

        return kryo;
    });

    // Reuse Output buffer to avoid repeated allocation of ByteArrayOutputStream
    private static final ThreadLocal<Output> outputThreadLocal = ThreadLocal.withInitial(() -> new Output(4096, -1));

    @Override
    public byte[] serialize(Object obj) {
        Kryo kryo = kryoThreadLocal.get();
        Output output = outputThreadLocal.get();
        output.reset(); // Clear buffer for new serialization

        try {
            kryo.writeClassAndObject(output, obj);
            return output.toBytes();
        } catch (Exception e) {
            log.error("Kryo 序列化失败: {}", obj.getClass().getName(), e);
            throw new RuntimeException("Kryo 序列化失败: " + obj.getClass().getName(), e);
        } finally {
            kryo.reset(); // Reset Kryo references
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        Kryo kryo = kryoThreadLocal.get();
        // Input is lightweight, passing byte array directly
        try (Input input = new Input(bytes)) {
            Object obj = kryo.readClassAndObject(input);
            return clazz.cast(obj);
        } catch (Exception e) {
            log.error("Kryo 反序列化失败, 目标类型: {}", clazz.getName(), e);
            throw new RuntimeException("Kryo 反序列化失败: " + clazz.getName(), e);
        } finally {
            kryo.reset(); // Reset Kryo references
        }
    }

    @Override
    public byte getCode() {
        return SerializerCode.KRYO_SERIALIZER;
    }
}
