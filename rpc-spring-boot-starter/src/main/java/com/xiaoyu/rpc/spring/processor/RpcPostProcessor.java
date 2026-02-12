package com.xiaoyu.rpc.spring.processor;

import com.xiaoyu.rpc.core.client.RpcClientProxy;
import com.xiaoyu.rpc.core.server.RpcServer;
import com.xiaoyu.rpc.spring.annotation.RpcReference;
import com.xiaoyu.rpc.spring.annotation.RpcService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPC Bean 后处理器
 * 负责扫描 @RpcService 并自动注册服务，
 * 同时处理 @RpcReference 字段并注入客户端代理。
 */
@Slf4j
public class RpcPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    private ApplicationContext applicationContext;
    private RpcServer rpcServer;

    // 代理缓存，避免重复创建
    private final Map<Class<?>, Object> proxyCache = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 处理 @RpcService：把服务注册到 RpcServer
        Class<?> beanClass = bean.getClass();
        if (beanClass.isAnnotationPresent(RpcService.class)) {
            registerService(bean, beanClass);
        }

        // 处理 @RpcReference：给字段注入代理对象
        injectRpcReferences(bean, beanClass);

        return bean;
    }

    /**
     * 注册服务到 RpcServer
     */
    @SuppressWarnings("unchecked")
    private void registerService(Object bean, Class<?> beanClass) {
        RpcService rpcService = beanClass.getAnnotation(RpcService.class);
        Class<?> interfaceClass = rpcService.interfaceClass();

        // 如果未指定接口，则使用第一个实现的接口
        if (interfaceClass == void.class) {
            Class<?>[] interfaces = beanClass.getInterfaces();
            if (interfaces.length == 0) {
                throw new RuntimeException("@RpcService 必须实现至少一个接口: " + beanClass.getName());
            }
            interfaceClass = interfaces[0];
        }

        // 延迟获取 RpcServer (因为可能还没初始化)
        if (rpcServer == null) {
            rpcServer = applicationContext.getBean(RpcServer.class);
        }

        log.info("注册 RPC 服务: {} -> {}", interfaceClass.getName(), beanClass.getName());
        rpcServer.register((Class<Object>) interfaceClass, bean);
    }

    /**
     * 注入 @RpcReference 标注的字段
     */
    private void injectRpcReferences(Object bean, Class<?> beanClass) {
        Field[] fields = beanClass.getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(RpcReference.class)) {
                Class<?> fieldType = field.getType();

                // 从缓存获取或创建代理
                Object proxy = proxyCache.computeIfAbsent(fieldType, this::createProxy);

                // 注入
                field.setAccessible(true);
                try {
                    field.set(bean, proxy);
                    log.info("注入 RPC 代理: {}.{}", beanClass.getSimpleName(), field.getName());
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("无法注入 RPC 代理到字段: " + field.getName(), e);
                }
            }
        }
    }

    /**
     * 创建 RPC 客户端代理
     */
    private Object createProxy(Class<?> interfaceClass) {
        return RpcClientProxy.create(interfaceClass);
    }
}
