package Serialization;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Getter
@Slf4j
public enum SerializerCode {
    JAVA_SERIALIZER((byte) 0x01),//JDK自带的序列化工具
    Kryo_SERIALIZER((byte) 0x02);//Kryo序列化工具

    private final byte code;

    public static Serializer getSerializerByCode(byte code) {//序列化器对象获取的统一接口
        // 这里目前直接返回你的实现类，以后可以用 Map 维护
        if (code == JAVA_SERIALIZER.getCode()) {
            return new JavaSerializerImpl();
        } else if (code == Kryo_SERIALIZER.getCode()) {
            return new KryoSerializerImpl();
        }

        throw new RuntimeException("没有对应的序列化器");//还找不到就抛出异常，后续可以考虑使用jdk的那个序列化工具来应对没有序列化器的情况
    }
}
