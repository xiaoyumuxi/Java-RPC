package Serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Slf4j
public class KryoSerializerImpl implements Serializer {
    //确保每个线程只创建一个 Kryo 对象并在该线程内复用，避免了并发冲突，也避免了每次序列化都 new Kryo() 的昂贵开销
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();

        kryo.setReferences(true);//处理循环引用的类

        // 关闭注册行为（为了开发方便，不强制要求注册类，虽然性能略低但在 RPC 场景通用性更好）
        kryo.setRegistrationRequired(false);
        
        // 对于 Protobuf 类，使用 Java 序列化作为后备方案
        kryo.addDefaultSerializer(com.google.protobuf.GeneratedMessageV3.class, JavaSerializer.class);

        return kryo;
    });

    @Override
    public byte[] serialize(Object obj) {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             Output output = new Output(byteArrayOutputStream)) {

            Kryo kryo = kryoThreadLocal.get();
            // 使用 writeClassAndObject 写入类型信息和对象数据
            kryo.writeClassAndObject(output, obj);

            output.flush();
            log.debug("Kryo 序列化成功: {}", obj.getClass().getName());
            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            log.error("Kryo 序列化失败: {}", obj.getClass().getName(), e);
            throw new RuntimeException("Kryo 序列化失败: " + obj.getClass().getName(), e);
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
             Input input = new Input(byteArrayInputStream)) {

            Kryo kryo = kryoThreadLocal.get();
            // 使用 readClassAndObject 读取类型信息和对象数据
            Object obj = kryo.readClassAndObject(input);
            log.debug("Kryo 反序列化成功: {}", obj.getClass().getName());
            return clazz.cast(obj);
        } catch (Exception e) {
            log.error("Kryo 反序列化失败, 目标类型: {}", clazz.getName(), e);
            throw new RuntimeException("Kryo 反序列化失败: " + clazz.getName(), e);
        }
    }

    @Override
    public byte getCode() {
        return SerializerCode.Kryo_SERIALIZER.getCode();
    }
}
