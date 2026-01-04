package Serialization;

import extension.SPI;

@SPI
public interface Serializer {
    /**
     * 序列化对象
     */
    byte[] serialize(Object obj);

    /**
     * 反序列化
     */
    <T> T deserialize(byte[] bytes, Class<T> clazz);

    /**
     * 序列化算法标识（用于协议头）
     */
    byte getCode();
}
