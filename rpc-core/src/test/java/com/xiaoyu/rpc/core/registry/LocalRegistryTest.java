package com.xiaoyu.rpc.core.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 本地注册中心单元测试
 */
@DisplayName("LocalRegistry 本地注册中心测试")
public class LocalRegistryTest {

    private LocalRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new LocalRegistry();
    }

    @AfterEach
    void tearDown() {
        registry.clearRegistry();
    }

    @Test
    @DisplayName("测试注册和查找服务")
    void testRegisterAndLookupService() {
        String serviceName = "com.example.TestService";
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 8080);

        // 注册服务
        registry.registerService(serviceName, address);

        // 查找服务
        InetSocketAddress result = registry.lookupService(serviceName);

        assertNotNull(result, "Should find registered service");
        assertEquals(address.getHostName(), result.getHostName(), "Host should match");
        assertEquals(address.getPort(), result.getPort(), "Port should match");
    }

    @Test
    @DisplayName("测试查找不存在的服务")
    void testLookupNonExistentService() {
        InetSocketAddress result = registry.lookupService("non.existent.Service");
        assertNull(result, "Should return null for non-existent service");
    }

    @Test
    @DisplayName("测试注册多个服务")
    void testRegisterMultipleServices() {
        String service1 = "com.example.Service1";
        String service2 = "com.example.Service2";
        InetSocketAddress address1 = new InetSocketAddress("127.0.0.1", 8081);
        InetSocketAddress address2 = new InetSocketAddress("127.0.0.1", 8082);

        registry.registerService(service1, address1);
        registry.registerService(service2, address2);

        InetSocketAddress result1 = registry.lookupService(service1);
        InetSocketAddress result2 = registry.lookupService(service2);

        assertNotNull(result1, "Should find first service");
        assertNotNull(result2, "Should find second service");
        assertEquals(8081, result1.getPort(), "First service port should match");
        assertEquals(8082, result2.getPort(), "Second service port should match");
    }

    @Test
    @DisplayName("测试覆盖注册服务")
    void testOverwriteService() {
        String serviceName = "com.example.OverwriteService";
        InetSocketAddress oldAddress = new InetSocketAddress("127.0.0.1", 8080);
        InetSocketAddress newAddress = new InetSocketAddress("127.0.0.1", 9090);

        registry.registerService(serviceName, oldAddress);
        registry.registerService(serviceName, newAddress); // 覆盖

        InetSocketAddress result = registry.lookupService(serviceName);

        assertNotNull(result, "Should find service");
        assertEquals(9090, result.getPort(), "Port should be updated to new address");
    }

    @Test
    @DisplayName("测试清空注册中心")
    void testClearRegistry() {
        String serviceName = "com.example.ToBeCleared";
        registry.registerService(serviceName, new InetSocketAddress("127.0.0.1", 8088));

        assertNotNull(registry.lookupService(serviceName), "Service should exist before clear");

        registry.clearRegistry();

        assertNull(registry.lookupService(serviceName), "Service should be removed after clear");
    }
}
