package com.xiaoyu.rpc.spring.annotation;

import java.lang.annotation.*;

/**
 * 标注在字段上，表示需要注入 RPC 服务代理。
 * Spring Boot Starter 会自动创建代理并注入。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RpcReference {

    /**
     * 服务版本号
     */
    String version() default "1.0";

    /**
     * 负载均衡策略 (roundrobin, random)
     */
    String loadBalancer() default "";
}
