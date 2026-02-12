package com.xiaoyu.rpc.core.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RPC 配置单元测试
 */
@DisplayName("RpcConfig 配置测试")
public class RpcConfigTest {

    @BeforeEach
    void setUp() throws Exception {
        // 重置单例以便每个测试独立
        resetSingleton();
        // 清理测试用的系统属性
        System.clearProperty("rpc.registry");
        System.clearProperty("rpc.serializer");
        System.clearProperty("rpc.server-port");
        System.clearProperty("rpc.transport");
        System.clearProperty("rpc.protocol");
    }

    @AfterEach
    void tearDown() throws Exception {
        // 清理系统属性
        System.clearProperty("rpc.registry");
        System.clearProperty("rpc.serializer");
        System.clearProperty("rpc.server-port");
        System.clearProperty("rpc.transport");
        System.clearProperty("rpc.protocol");
        // 重置单例
        resetSingleton();
    }

    private void resetSingleton() throws Exception {
        Field instanceField = RpcConfig.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    @Test
    @DisplayName("测试单例模式")
    void testSingletonPattern() {
        RpcConfig config1 = RpcConfig.getInstance();
        RpcConfig config2 = RpcConfig.getInstance();

        assertSame(config1, config2, "RpcConfig should be singleton");
    }

    @Test
    @DisplayName("测试默认配置值")
    void testDefaultConfigValues() {
        RpcConfig config = RpcConfig.getInstance();

        assertNotNull(config.getSerializerType(), "Serializer type should not be null");
        assertNotNull(config.getServerHost(), "Server host should not be null");
        assertNotNull(config.getServerPort(), "Server port should not be null");
        assertNotNull(config.getProtocol(), "Protocol should not be null");
    }

    @Test
    @DisplayName("测试系统属性覆盖 - 注册中心类型")
    void testSystemPropertyOverrideRegistry() throws Exception {
        System.setProperty("rpc.registry", "local");
        resetSingleton();

        RpcConfig config = RpcConfig.getInstance();
        assertEquals("local", config.getRegistryType(), "Registry type should be overridden by system property");
    }

    @Test
    @DisplayName("测试系统属性覆盖 - 序列化器")
    void testSystemPropertyOverrideSerializer() throws Exception {
        System.setProperty("rpc.serializer", "kryo");
        resetSingleton();

        RpcConfig config = RpcConfig.getInstance();
        assertEquals("kryo", config.getSerializerType(), "Serializer should be overridden by system property");
    }

    @Test
    @DisplayName("测试系统属性覆盖 - 服务端口")
    void testSystemPropertyOverridePort() throws Exception {
        System.setProperty("rpc.server-port", "9999");
        resetSingleton();

        RpcConfig config = RpcConfig.getInstance();
        assertEquals(9999, config.getServerPort(), "Server port should be overridden by system property");
    }

    @Test
    @DisplayName("测试系统属性覆盖 - 传输层")
    void testSystemPropertyOverrideTransport() throws Exception {
        System.setProperty("rpc.transport", "netty");
        resetSingleton();

        RpcConfig config = RpcConfig.getInstance();
        assertEquals("netty", config.getTransport(), "Transport should be overridden by system property");
    }

    @Test
    @DisplayName("测试系统属性覆盖 - 协议")
    void testSystemPropertyOverrideProtocol() throws Exception {
        System.setProperty("rpc.protocol", "grpc");
        resetSingleton();

        RpcConfig config = RpcConfig.getInstance();
        assertEquals("grpc", config.getProtocol(), "Protocol should be overridden by system property");
    }

    @Test
    @DisplayName("测试 getSerializerCode 方法")
    void testGetSerializerCode() {
        System.setProperty("rpc.serializer", "kryo");
        RpcConfig config = RpcConfig.getInstance();

        byte code = config.getSerializerCode();
        assertTrue(code > 0, "Serializer code should be positive");
    }

    @Test
    @DisplayName("测试 toString 方法")
    void testToString() {
        RpcConfig config = RpcConfig.getInstance();
        String str = config.toString();

        assertNotNull(str, "toString should not return null");
        assertTrue(str.contains("RpcConfig"), "toString should contain class name");
        assertTrue(str.contains("serializerType"), "toString should contain serializerType");
        assertTrue(str.contains("serverPort"), "toString should contain serverPort");
    }
}
