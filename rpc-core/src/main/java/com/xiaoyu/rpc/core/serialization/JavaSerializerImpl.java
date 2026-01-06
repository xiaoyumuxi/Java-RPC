package com.xiaoyu.rpc.core.serialization;

import lombok.extern.slf4j.Slf4j;

import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;
import java.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaSerializerImpl implements Serializer {
    private static final Logger log = LoggerFactory.getLogger(JavaSerializerImpl.class);

    // 使用jdk自带的对象流来进行序列化
    @Override
    public byte[] serialize(Object obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            // 将对象写入输出流
            oos.writeObject(obj);
            oos.flush();
            log.info("正在使用JavaSerializer序列化对象：{}", obj);
            // 返回字节数组
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("序列化失败", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                ObjectInputStream ois = new ObjectInputStream(bis)) {
            // 从字节流中读取对象
            Object obj = ois.readObject();
            log.info("正在使用JavaSerializer反序列化对象：{}", obj);
            // 强转并返回
            return clazz.cast(obj);
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("反序列化失败", e);
        }
    }

    @Override
    public byte getCode() {
        return SerializerCode.JAVA_SERIALIZER;
    }
}
