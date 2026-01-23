package com.xiaoyu.rpc.spring.annotation;

import java.lang.annotation.*;

/**
 * 标注在服务实现类上，表示该类是一个 RPC 服务提供者。
 * Spring Boot Starter 会自动扫描并注册到 RPC 注册中心。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface RpcService {

    /**
     * 服务接口类。如果不指定，则默认使用实现类的第一个接口。
     */
    Class<?> interfaceClass() default void.class;

    /**
     * 服务版本号
     */
    String version() default "1.0";
}
