package com.xiaoyu.rpc.core.registry.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.xiaoyu.rpc.core.loadbalancer.LoadBalancer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NacosServiceDiscovery 缓存与回退测试")
public class NacosServiceDiscoveryTest {

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        Field cacheField = NacosServiceDiscovery.class.getDeclaredField("serviceCache");
        cacheField.setAccessible(true);
        ((Map<String, List<Instance>>) cacheField.get(null)).clear();

        Field subscribedField = NacosServiceDiscovery.class.getDeclaredField("subscribedServices");
        subscribedField.setAccessible(true);
        ((Set<String>) subscribedField.get(null)).clear();
    }

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

        LoadBalancer loadBalancer = addresses -> addresses.get(0);
        NacosServiceDiscovery discovery = new NacosServiceDiscovery(namingService, loadBalancer);

        InetSocketAddress first = discovery.lookupService("svc-a");
        InetSocketAddress second = discovery.lookupService("svc-a");

        assertEquals("10.0.0.1", first.getHostString());
        assertEquals(8080, first.getPort());
        assertEquals("10.0.0.1", second.getHostString());
        assertEquals(1, subscribeCount.get(), "Same service should only subscribe once");
    }

    @Test
    @DisplayName("Nacos 异常时回退到本地缓存")
    @SuppressWarnings("unchecked")
    void testFallbackToCacheOnNacosError() throws Exception {
        Field cacheField = NacosServiceDiscovery.class.getDeclaredField("serviceCache");
        cacheField.setAccessible(true);
        Map<String, List<Instance>> cache = (Map<String, List<Instance>>) cacheField.get(null);
        cache.put("svc-b", new ArrayList<>(List.of(instance("127.0.0.1", 9000))));

        NamingService namingService = namingServiceProxy((method, args) -> {
            if ("getAllInstances".equals(method) && args.length == 1) {
                throw new NacosException(500, "network down");
            }
            if ("subscribe".equals(method)) {
                return null;
            }
            return null;
        });

        LoadBalancer loadBalancer = addresses -> addresses.get(0);
        NacosServiceDiscovery discovery = new NacosServiceDiscovery(namingService, loadBalancer);

        InetSocketAddress address = discovery.lookupService("svc-b");
        assertEquals("127.0.0.1", address.getHostString());
        assertEquals(9000, address.getPort());
    }

    @Test
    @DisplayName("Nacos 空列表且无缓存时抛异常")
    void testNoInstanceAndNoCache() {
        NamingService namingService = namingServiceProxy((method, args) -> {
            if ("getAllInstances".equals(method) && args.length == 1) {
                return List.of();
            }
            if ("subscribe".equals(method)) {
                return null;
            }
            return null;
        });

        LoadBalancer loadBalancer = addresses -> addresses.get(0);
        NacosServiceDiscovery discovery = new NacosServiceDiscovery(namingService, loadBalancer);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> discovery.lookupService("svc-empty"));
        assertTrue(ex.getMessage().contains("未找到服务"), "Should throw not found error");
    }

    private static Instance instance(String ip, int port) {
        Instance i = new Instance();
        i.setIp(ip);
        i.setPort(port);
        return i;
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
                        if (method.getReturnType() == boolean.class) {
                            return false;
                        }
                        if (method.getReturnType() == byte.class) {
                            return (byte) 0;
                        }
                        if (method.getReturnType() == short.class) {
                            return (short) 0;
                        }
                        if (method.getReturnType() == int.class) {
                            return 0;
                        }
                        if (method.getReturnType() == long.class) {
                            return 0L;
                        }
                        if (method.getReturnType() == float.class) {
                            return 0F;
                        }
                        if (method.getReturnType() == double.class) {
                            return 0D;
                        }
                        if (method.getReturnType() == char.class) {
                            return '\0';
                        }
                    }
                    return result;
                });
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args) throws Throwable;
    }
}
