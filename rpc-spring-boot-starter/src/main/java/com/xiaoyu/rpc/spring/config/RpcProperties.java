package com.xiaoyu.rpc.spring.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RPC 配置属性，映射 application.yml 中的 rpc.* 配置
 */
@Data
@ConfigurationProperties(prefix = "rpc")
public class RpcProperties {

    /**
     * 传输层实现 (netty)
     */
    private String transport = "netty";

    /**
     * 协议类型 (netty, http, http2, grpc)
     */
    private String protocol = "netty";

    /**
     * 服务端绑定主机
     */
    private String serverHost = "127.0.0.1";

    /**
     * 服务端绑定端口
     */
    private int serverPort = 8080;

    /**
     * 注册中心类型 (nacos, local)
     */
    private String registry = "nacos";

    /**
     * 注册中心地址
     */
    private String registryAddress = "127.0.0.1:8848";

    /**
     * 序列化方式 (kryo, protobuf, json, java)
     */
    private String serializer = "kryo";

    /**
     * 代理方式 (jdk, bytebuddy)
     */
    private String proxy = "bytebuddy";

    /**
     * 负载均衡策略 (roundrobin, random)
     */
    private String loadBalancer = "roundrobin";

    /**
     * 是否启用服务端 (Provider 模式)
     */
    private boolean serverEnabled = true;
}
