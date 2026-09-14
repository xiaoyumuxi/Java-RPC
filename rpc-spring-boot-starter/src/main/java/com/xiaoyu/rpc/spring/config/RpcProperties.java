package com.xiaoyu.rpc.spring.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RPC 配置属性，映射 application.yml 中的 rpc.* 配置
 */
@Data
@ConfigurationProperties(prefix = "rpc")
public class RpcProperties {

    /** 传输层实现 (netty) */
    private String transport = "netty";

    /** 协议类型 (netty, http, http2, grpc) */
    private String protocol = "netty";

    /** 服务端绑定主机 */
    private String serverHost = "127.0.0.1";

    /** 服务端绑定端口 */
    private int serverPort = 8080;

    /** 注册中心类型 (nacos, local) */
    private String registry = "nacos";

    /** 注册中心地址 */
    private String registryAddress = "127.0.0.1:8848";

    /** 序列化方式 (kryo, protobuf, json, java) */
    private String serializer = "kryo";

    /** 代理方式 (jdk, bytebuddy) */
    private String proxy = "bytebuddy";

    /** 负载均衡策略 (roundrobin, random) */
    private String loadBalancer = "roundrobin";

    /** 最大 RPC 报文大小 */
    private int maxMessageSize = 8 * 1024 * 1024;

    /** 单次 RPC 请求超时（毫秒） */
    private int requestTimeoutMs = 5000;

    /** Netty worker 线程数，0 表示使用 CPU cores * 2 */
    private int workerThreads = 0;

    /** Netty boss 线程数 */
    private int bossThreads = 1;

    /** 服务端业务线程数，0 表示使用 CPU cores */
    private int businessThreads = 0;

    /** 服务端业务线程池等待队列容量 */
    private int businessQueueCapacity = 1000;

    /** 客户端最大缓存连接数 */
    private int maxConnections = 100;

    /** 是否启用服务端 (Provider 模式) */
    private boolean serverEnabled = true;
}
