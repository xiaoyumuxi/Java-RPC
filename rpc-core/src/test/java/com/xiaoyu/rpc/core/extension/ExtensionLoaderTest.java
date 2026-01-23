package com.xiaoyu.rpc.core.extension;

import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.core.loadbalancer.LoadBalancer;
import com.xiaoyu.rpc.core.client.ProxyFactory;

import com.xiaoyu.rpc.core.registry.ServiceRegistry;
import com.xiaoyu.rpc.core.registry.ServiceDiscovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExtensionLoader SPI 机制单元测试
 */
@DisplayName("SPI ExtensionLoader 测试")
public class ExtensionLoaderTest {

    @Test
    @DisplayName("测试加载 Serializer 扩展")
    void testLoadSerializerExtensions() {
        ExtensionLoader<Serializer> loader = ExtensionLoader.getExtensionLoader(Serializer.class);

        // 测试所有支持的序列化器
        assertNotNull(loader.getExtension("java"), "Java serializer should be loaded");
        assertNotNull(loader.getExtension("kryo"), "Kryo serializer should be loaded");
        assertNotNull(loader.getExtension("protobuf"), "Protobuf serializer should be loaded");
        assertNotNull(loader.getExtension("json"), "JSON serializer should be loaded");
    }

    @Test
    @DisplayName("测试获取所有支持扩展名")
    void testGetSupportedExtensions() {
        ExtensionLoader<Serializer> loader = ExtensionLoader.getExtensionLoader(Serializer.class);

        var extensions = loader.getSupportedExtensions();
        assertTrue(extensions.contains("java"), "Should contain 'java' extension");
        assertTrue(extensions.contains("kryo"), "Should contain 'kryo' extension");
        assertTrue(extensions.contains("protobuf"), "Should contain 'protobuf' extension");
        assertTrue(extensions.contains("json"), "Should contain 'json' extension");
        assertEquals(4, extensions.size(), "Should have exactly 4 serializer extensions");
    }

    @Test
    @DisplayName("测试扩展单例缓存")
    void testExtensionCaching() {
        ExtensionLoader<Serializer> loader = ExtensionLoader.getExtensionLoader(Serializer.class);

        Serializer first = loader.getExtension("kryo");
        Serializer second = loader.getExtension("kryo");

        assertSame(first, second, "Same extension should return same instance (singleton)");
    }

    @Test
    @DisplayName("测试加载不存在扩展时抛出异常")
    void testLoadNonExistentExtension() {
        ExtensionLoader<Serializer> loader = ExtensionLoader.getExtensionLoader(Serializer.class);

        assertThrows(RuntimeException.class, () -> {
            loader.getExtension("non_existent");
        }, "Loading non-existent extension should throw exception");
    }

    @Test
    @DisplayName("测试 LoadBalancer 扩展加载")
    void testLoadBalancerExtensions() {
        ExtensionLoader<LoadBalancer> loader = ExtensionLoader.getExtensionLoader(LoadBalancer.class);

        assertNotNull(loader.getExtension("random"), "Random load balancer should be loaded");
        assertNotNull(loader.getExtension("roundrobin"), "RoundRobin load balancer should be loaded");

        var extensions = loader.getSupportedExtensions();
        assertEquals(2, extensions.size(), "Should have exactly 2 load balancer extensions");
    }

    @Test
    @DisplayName("测试 ProxyFactory 扩展加载")
    void testProxyFactoryExtensions() {
        ExtensionLoader<ProxyFactory> loader = ExtensionLoader.getExtensionLoader(ProxyFactory.class);

        assertNotNull(loader.getExtension("jdk"), "JDK proxy factory should be loaded");
        assertNotNull(loader.getExtension("bytebuddy"), "ByteBuddy proxy factory should be loaded");

        var extensions = loader.getSupportedExtensions();
        assertEquals(2, extensions.size(), "Should have exactly 2 proxy factory extensions");
    }

    @Test
    @DisplayName("测试 ServiceRegistry 扩展加载")
    void testServiceRegistryExtensions() {
        ExtensionLoader<ServiceRegistry> loader = ExtensionLoader.getExtensionLoader(ServiceRegistry.class);

        // local registry should be loadable without external dependencies
        assertNotNull(loader.getExtension("local"), "Local service registry should be loaded");

        var extensions = loader.getSupportedExtensions();
        assertTrue(extensions.contains("local"), "Should contain 'local' extension");
        assertTrue(extensions.contains("nacos"), "Should contain 'nacos' extension");
    }

    @Test
    @DisplayName("测试 ServiceDiscovery 扩展加载")
    void testServiceDiscoveryExtensions() {
        ExtensionLoader<ServiceDiscovery> loader = ExtensionLoader.getExtensionLoader(ServiceDiscovery.class);

        // local registry should be loadable without external dependencies
        assertNotNull(loader.getExtension("local"), "Local service discovery should be loaded");

        var extensions = loader.getSupportedExtensions();
        assertTrue(extensions.contains("local"), "Should contain 'local' extension");
        assertTrue(extensions.contains("nacos"), "Should contain 'nacos' extension");
    }
}
