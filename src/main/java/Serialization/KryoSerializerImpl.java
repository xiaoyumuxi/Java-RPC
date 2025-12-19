package Serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class KryoSerializerImpl implements Serializer {
    //确保每个线程只创建一个 Kryo 对象并在该线程内复用，避免了并发冲突，也避免了每次序列化都 new Kryo() 的昂贵开销
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();

        kryo.setReferences(true);//处理循环引用的类

        // 关闭注册行为（为了开发方便，不强制要求注册类，虽然性能略低但在 RPC 场景通用性更好）
        kryo.setRegistrationRequired(false);

        return kryo;
    });

    @Override
    public byte[] serialize(Object obj) {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             Output output = new Output(byteArrayOutputStream)) {

            Kryo kryo = kryoThreadLocal.get();
            // 将对象写入 Output
            kryo.writeObject(output, obj);

            output.flush();
            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Kryo 序列化失败", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
             Input input = new Input(byteArrayInputStream)) {

            Kryo kryo = kryoThreadLocal.get();
            // 从 Input 读取对象
            return kryo.readObject(input, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Kryo 反序列化失败", e);
        }
    }

    @Override
    public byte getCode() {
        return SerializerCode.Kryo_SERIALIZER.getCode();
    }
}
