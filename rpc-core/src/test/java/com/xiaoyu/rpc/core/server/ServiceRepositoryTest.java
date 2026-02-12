package com.xiaoyu.rpc.core.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ServiceRepository 服务仓库测试")
public class ServiceRepositoryTest {

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        Field mapField = ServiceRepository.class.getDeclaredField("SERVICE_MAP");
        mapField.setAccessible(true);
        ((Map<String, Object>) mapField.get(null)).clear();
    }

    @Test
    @DisplayName("测试注册并查询服务")
    void testRegisterAndGetService() {
        String serviceName = "com.example.TestService";
        Object serviceBean = new Object();

        ServiceRepository.registerService(serviceName, serviceBean);

        assertSame(serviceBean, ServiceRepository.getService(serviceName), "Should return same registered bean");
    }

    @Test
    @DisplayName("测试覆盖注册服务")
    void testOverwriteService() {
        String serviceName = "com.example.TestService";
        Object oldBean = new Object();
        Object newBean = new Object();

        ServiceRepository.registerService(serviceName, oldBean);
        ServiceRepository.registerService(serviceName, newBean);

        assertSame(newBean, ServiceRepository.getService(serviceName), "Latest registration should overwrite old one");
    }

    @Test
    @DisplayName("测试查询不存在的服务")
    void testGetNonExistentService() {
        assertNull(ServiceRepository.getService("com.example.NotFound"), "Unknown service should return null");
    }
}
