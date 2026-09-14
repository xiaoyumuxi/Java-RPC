package com.xiaoyu.rpc.core.registry.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.xiaoyu.rpc.core.loadbalancer.LoadBalancer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("NacosServiceDiscovery 缓存、订阅与关闭测试")
public class NacosServiceDiscoveryTest {

    @Test
    @DisplayName("正常发现服务并且同服务只订阅一次")
    void testLookupAndSubscribeOnce() {
        AtomicInteger subscribeCount = new AtomicInteger();
        List<Instance> instances = List.of(instance("10.0.0.1", 8080), instance("10.0.0.2", 8081));

        NamingService namingService = namingServiceProxy((method, args) -> {
            if ("getAllInstances".equals(method) && args.length == 1) {
                return instances;
            }
            if ("subscribe".equals(method) && args.length == 2 && args[1] instanceof EventListener) {
                subscribeCount.incrementAndGet();
                return null;
            }
            return null;
        });

        NacosServiceDiscovery discovery = new NacosServiceDiscovery(namingService, firstAddress());
        InetSocketAddress first = discovery.lookupService("svc-a");
        InetSocketAddress second = discovery.lookupService("svc-a");

        assertEquals("10.0.0.1", first.getHostString());
        assertEquals(8080, first.getPort());
        assertEquals("10.0.0.1", second.getHostString());
        assertEquals(1, subscribeCount.get());
    }

    @Test
    @DisplayName("Nacos 网络异常时回退到最近一次成功缓存")
    void testFallbackToCacheOnNacosError() {
        AtomicInteger lookupCount = new AtomicInteger();
        NamingService namingService = namingServiceProxy((method, args) -> {
            if ("getAllInstances".equals(method) && args.length == 1) {
                if (lookupCount.getAndIncrement() == 0) {
                    return List.of(instance("127.0.0.1", 9000));
                }
                throw new NacosException(500, "network down");
            }
            return null;
        });

        NacosServiceDiscovery discovery = new NacosServiceDiscovery(namingService, firstAddress());
        discovery.lookupService("svc-b");
        InetSocketAddress cached = discovery.lookupService("svc-b");

        assertEquals("127.0.0.1", cached.getHostString());
        assertEquals(9000, cached.getPort());
    }

    @Test
    @DisplayName("Nacos 明确返回空实例时清理旧缓存，不再路由到下线节点")
    void testEmptyAuthoritativeResultInvalidatesCache() {
        AtomicInteger lookupCount = new AtomicInteger();
        NamingService namingService = namingServiceProxy((method, args) -> {
            if ("getAllInstances".equals(method) && args.length == 1) {
                int index = lookupCount.getAndIncrement();
                if (index == 0) {
                    return List.of(instance("10.0.0.9", 8080));
                }
                if (index == 1) {
                    return List.of();
                }
                throw new NacosException(500, "network down after empty result");
            }
            return null;
        });

        NacosServiceDiscovery discovery = new NacosServiceDiscovery(namingService, firstAddress());
        discovery.lookupService("svc-stale");

        RuntimeException notFound = assertThrows(RuntimeException.class,
                () -> discovery.lookupService("svc-stale"));
        assertTrue(notFound.getMessage().contains("未找到服务"));

        RuntimeException noFallback = assertThrows(RuntimeException.class,
                () -> discovery.lookupService("svc-stale"));
        assertTrue(noFallback.getMessage().contains("无缓存可用"));
    }

    @Test
    @DisplayName("订阅事件为空时删除缓存")
    void testUpdateCacheRemovesEmptyInstances() {
        NamingService namingService = namingServiceProxy((method, args) -> null);
        NacosServiceDiscovery discovery = new NacosServiceDiscovery(namingService, firstAddress());

        discovery.updateCache("svc-c", List.of(instance("10.0.0.1", 8080)));
        discovery.updateCache("svc-c", List.of());

        NamingService failingService = namingServiceProxy((method, args) -> {
            if ("getAllInstances".equals(method)) {
                throw new NacosException(500, "network down");
            }
            return null;
        });
        NacosServiceDiscovery emptyDiscovery = new NacosServiceDiscovery(failingService, firstAddress());
        assertThrows(RuntimeException.class, () -> emptyDiscovery.lookupService("svc-c"));
    }

    @Test
    @DisplayName("close 幂等取消订阅并关闭 NamingService")
    void testCloseUnsubscribesAndShutsDown() {
        AtomicInteger unsubscribeCount = new AtomicInteger();
        AtomicInteger shutdownCount = new AtomicInteger();
        NamingService namingService = namingServiceProxy((method, args) -> {
            if ("getAllInstances".equals(method) && args.length == 1) {
                return List.of(instance("127.0.0.1", 8080));
            }
            if ("unsubscribe".equals(method)) {
                unsubscribeCount.incrementAndGet();
            }
            if ("shutDown".equals(method)) {
                shutdownCount.incrementAndGet();
            }
            return null;
        });

        NacosServiceDiscovery discovery = new NacosServiceDiscovery(namingService, firstAddress());
        discovery.lookupService("svc-close");
        discovery.close();
        discovery.close();

        assertEquals(1, unsubscribeCount.get());
        assertEquals(1, shutdownCount.get());
        assertThrows(IllegalStateException.class, () -> discovery.lookupService("svc-close"));
    }

    private static LoadBalancer firstAddress() {
        return addresses -> addresses.get(0);
    }

    private static Instance instance(String ip, int port) {
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(port);
        return instance;
    }

    private static NamingService namingServiceProxy(Invocation invocation) {
        return (NamingService) Proxy.newProxyInstance(
                NacosServiceDiscoveryTest.class.getClassLoader(),
                new Class<?>[] { NamingService.class },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "NamingServiceProxy";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    Object result = invocation.invoke(method.getName(), args == null ? new Object[0] : args);
                    if (result == null && method.getReturnType().isPrimitive()) {
                        if (method.getReturnType() == boolean.class) return false;
                        if (method.getReturnType() == byte.class) return (byte) 0;
                        if (method.getReturnType() == short.class) return (short) 0;
                        if (method.getReturnType() == int.class) return 0;
                        if (method.getReturnType() == long.class) return 0L;
                        if (method.getReturnType() == float.class) return 0F;
                        if (method.getReturnType() == double.class) return 0D;
                        if (method.getReturnType() == char.class) return '\0';
                    }
                    return result;
                });
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args) throws Throwable;
    }
}
