package com.xiaoyu.rpc.core.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RPC 配置单元测试
 */
@DisplayName("RpcConfig 配置测试")
public class RpcConfigTest {

    private static final String[] RPC_SYSTEM_PROPERTIES = {
            "rpc.registry",
            "rpc.serializer",
            "rpc.server-host",
            "rpc.server-port",
            "rpc.registry-address",
            "rpc.transport",
            "rpc.protocol",
            "rpc.proxy",
            "rpc.load-balancer",
            "rpc.max-message-size",
            "rpc.request-timeout-ms",
            "rpc.worker-threads",
            "rpc.boss-threads",
            "rpc.business-threads",
            "rpc.business-queue-capacity",
            "rpc.max-connections"
    };

    @BeforeEach
    void setUp() throws Exception {
        resetSingleton();
        clearRpcSystemProperties();
        // 单元测试不依赖外部 Nacos。
        System.setProperty("rpc.registry", "local");
    }

    @AfterEach
    void tearDown() throws Exception {
        clearRpcSystemProperties();
        resetSingleton();
    }

    private void resetSingleton() throws Exception {
        Field instanceField = RpcConfig.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    private void clearRpcSystemProperties() {
        for (String key : RPC_SYSTEM_PROPERTIES) {
            System.clearProperty(key);
        }
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

        assertNotNull(config.getSerializerType());
        assertNotNull(config.getServerHost());
        assertNotNull(config.getServerPort());
        assertNotNull(config.getProtocol());
        assertTrue(config.getRequestTimeoutMillis() > 0);
        assertTrue(config.getBusinessQueueCapacity() > 0);
    }

    @Test
    @DisplayName("测试系统属性覆盖 - 注册中心类型")
    void testSystemPropertyOverrideRegistry() throws Exception {
        System.setProperty("rpc.registry", "local");
        resetSingleton();

        assertEquals("local", RpcConfig.getInstance().getRegistryType());
    }

    @Test
    @DisplayName("测试系统属性覆盖 - 序列化器")
    void testSystemPropertyOverrideSerializer() throws Exception {
        System.setProperty("rpc.serializer", "kryo");
        resetSingleton();

        assertEquals("kryo", RpcConfig.getInstance().getSerializerType());
    }

    @Test
    @DisplayName("测试系统属性覆盖 - 服务端口")
    void testSystemPropertyOverridePort() throws Exception {
        System.setProperty("rpc.server-port", "9999");
        resetSingleton();

        assertEquals(9999, RpcConfig.getInstance().getServerPort());
    }

    @Test
    @DisplayName("测试系统属性覆盖 - 传输层")
    void testSystemPropertyOverrideTransport() throws Exception {
        System.setProperty("rpc.transport", "netty");
        resetSingleton();

        assertEquals("netty", RpcConfig.getInstance().getTransport());
    }

    @Test
    @DisplayName("测试系统属性覆盖 - 协议")
    void testSystemPropertyOverrideProtocol() throws Exception {
        System.setProperty("rpc.protocol", "grpc");
        resetSingleton();

        assertEquals("grpc", RpcConfig.getInstance().getProtocol());
    }

    @Test
    @DisplayName("测试 Spring Boot 使用的扩展系统属性全部生效")
    void testExtendedSystemPropertyOverrides() throws Exception {
        System.setProperty("rpc.server-host", "0.0.0.0");
        System.setProperty("rpc.registry-address", "10.0.0.8:8848");
        System.setProperty("rpc.proxy", "jdk");
        System.setProperty("rpc.load-balancer", "random");
        System.setProperty("rpc.max-message-size", "1048576");
        System.setProperty("rpc.request-timeout-ms", "2500");
        System.setProperty("rpc.worker-threads", "6");
        System.setProperty("rpc.boss-threads", "2");
        System.setProperty("rpc.business-threads", "8");
        System.setProperty("rpc.business-queue-capacity", "256");
        System.setProperty("rpc.max-connections", "64");
        resetSingleton();

        RpcConfig config = RpcConfig.getInstance();
        assertEquals("0.0.0.0", config.getServerHost());
        assertEquals("10.0.0.8:8848", config.getRegistryAddress());
        assertEquals("jdk", config.getProxyType());
        assertEquals("random", config.getLoadBalancer());
        assertEquals(1048576, config.getMaxMessageSize());
        assertEquals(2500, config.getRequestTimeoutMillis());
        assertEquals(6, config.getWorkerThreads());
        assertEquals(2, config.getBossThreads());
        assertEquals(8, config.getBusinessThreads());
        assertEquals(256, config.getBusinessQueueCapacity());
        assertEquals(64, config.getMaxConnections());
    }

    @Test
    @DisplayName("非法整数系统属性不会破坏配置加载")
    void testInvalidIntegerOverrideFallsBack() throws Exception {
        System.setProperty("rpc.request-timeout-ms", "not-a-number");
        resetSingleton();

        RpcConfig config = RpcConfig.getInstance();
        assertEquals(5000, config.getRequestTimeoutMillis());
    }

    @Test
    @DisplayName("测试 getSerializerCode 方法")
    void testGetSerializerCode() throws Exception {
        System.setProperty("rpc.serializer", "kryo");
        resetSingleton();
        RpcConfig config = RpcConfig.getInstance();

        byte code = config.getSerializerCode();
        assertTrue(code > 0, "Serializer code should be positive");
    }

    @Test
    @DisplayName("测试 toString 方法")
    void testToString() {
        RpcConfig config = RpcConfig.getInstance();
        String str = config.toString();

        assertNotNull(str);
        assertTrue(str.contains("RpcConfig"));
        assertTrue(str.contains("serializerType"));
        assertTrue(str.contains("serverPort"));
        assertTrue(str.contains("requestTimeoutMillis"));
    }
}
