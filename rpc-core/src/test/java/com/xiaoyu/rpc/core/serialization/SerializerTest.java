package com.xiaoyu.rpc.core.serialization;

import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.Serializable;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 序列化器单元测试
 * 测试 Java、Kryo、JSON 序列化器的序列化/反序列化功能
 */
@DisplayName("Serializer 序列化器测试")
public class SerializerTest {

    /**
     * 用于测试的简单可序列化对象
     */
    public static class TestMessage implements Serializable {
        private static final long serialVersionUID = 1L;

        private String interfaceName;
        private String methodName;
        private String[] paramTypes;

        public TestMessage() {
        }

        public TestMessage(String interfaceName, String methodName, String[] paramTypes) {
            this.interfaceName = interfaceName;
            this.methodName = methodName;
            this.paramTypes = paramTypes;
        }

        public String getInterfaceName() {
            return interfaceName;
        }

        public void setInterfaceName(String interfaceName) {
            this.interfaceName = interfaceName;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String[] getParamTypes() {
            return paramTypes;
        }

        public void setParamTypes(String[] paramTypes) {
            this.paramTypes = paramTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            TestMessage that = (TestMessage) o;
            return Objects.equals(interfaceName, that.interfaceName) &&
                    Objects.equals(methodName, that.methodName);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "java", "kryo", "json" })
    @DisplayName("测试序列化和反序列化 TestMessage")
    void testSerializeTestMessage(String serializerName) {
        Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension(serializerName);

        // 创建测试消息
        TestMessage message = new TestMessage(
                "com.example.HelloService",
                "sayHello",
                new String[] { "java.lang.String" });

        // 序列化
        byte[] bytes = serializer.serialize(message);
        assertNotNull(bytes, "Serialized bytes should not be null");
        assertTrue(bytes.length > 0, "Serialized bytes should have content");

        // 反序列化
        TestMessage deserialized = serializer.deserialize(bytes, TestMessage.class);
        assertNotNull(deserialized, "Deserialized message should not be null");
        assertEquals(message.getInterfaceName(), deserialized.getInterfaceName(), "Interface name should match");
        assertEquals(message.getMethodName(), deserialized.getMethodName(), "Method name should match");
    }

    @ParameterizedTest
    @ValueSource(strings = { "java", "kryo", "json" })
    @DisplayName("测试序列化和反序列化简单字符串")
    void testSerializeString(String serializerName) {
        Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension(serializerName);

        String original = "Hello, World!";

        // 序列化
        byte[] bytes = serializer.serialize(original);
        assertNotNull(bytes, "Serialized bytes should not be null");

        // 反序列化
        String deserialized = serializer.deserialize(bytes, String.class);
        assertEquals(original, deserialized, "Deserialized string should match original");
    }

    @Test
    @DisplayName("测试 Java 序列化器代码")
    void testJavaSerializerCode() {
        Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension("java");
        assertEquals(SerializerCode.JAVA_SERIALIZER, serializer.getCode(), "Java serializer should have code 0x01");
    }

    @Test
    @DisplayName("测试 Kryo 序列化器代码")
    void testKryoSerializerCode() {
        Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension("kryo");
        assertEquals(SerializerCode.KRYO_SERIALIZER, serializer.getCode(), "Kryo serializer should have code 0x02");
    }

    @Test
    @DisplayName("测试 Protobuf 序列化器代码")
    void testProtobufSerializerCode() {
        Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension("protobuf");
        assertEquals(SerializerCode.PROTOBUF_SERIALIZER, serializer.getCode(),
                "Protobuf serializer should have code 0x03");
    }

    @Test
    @DisplayName("测试 JSON 序列化器代码")
    void testJsonSerializerCode() {
        Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension("json");
        // JSON serializer uses code 0x04
        assertEquals((byte) 0x04, serializer.getCode(), "JSON serializer should have code 0x04");
    }

    @Test
    @DisplayName("测试 SerializerCode 按名称获取序列化器")
    void testGetSerializerByName() {
        Serializer javaSerializer = SerializerCode.getSerializerByName("java");
        assertNotNull(javaSerializer, "Should get Java serializer by name");

        Serializer kryoSerializer = SerializerCode.getSerializerByName("kryo");
        assertNotNull(kryoSerializer, "Should get Kryo serializer by name");

        // 测试大小写不敏感
        Serializer protobufSerializer = SerializerCode.getSerializerByName("PROTOBUF");
        assertNotNull(protobufSerializer, "Should get Protobuf serializer by uppercase name");
    }

    @Test
    @DisplayName("测试 SerializerCode 按代码获取序列化器")
    void testGetSerializerByCode() {
        Serializer javaSerializer = SerializerCode.getSerializerByCode(SerializerCode.JAVA_SERIALIZER);
        assertNotNull(javaSerializer, "Should get serializer by code 0x01");

        Serializer kryoSerializer = SerializerCode.getSerializerByCode(SerializerCode.KRYO_SERIALIZER);
        assertNotNull(kryoSerializer, "Should get serializer by code 0x02");

        Serializer protobufSerializer = SerializerCode.getSerializerByCode(SerializerCode.PROTOBUF_SERIALIZER);
        assertNotNull(protobufSerializer, "Should get serializer by code 0x03");
    }

    @Test
    @DisplayName("测试空对象序列化")
    void testSerializeNull() {
        Serializer jsonSerializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension("json");

        byte[] bytes = jsonSerializer.serialize(null);
        assertNotNull(bytes, "Serialized null should return empty array");
    }
}
