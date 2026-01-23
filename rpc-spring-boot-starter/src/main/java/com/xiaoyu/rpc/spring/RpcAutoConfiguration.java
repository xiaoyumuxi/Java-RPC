package com.xiaoyu.rpc.spring;

import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.server.RpcServer;
import com.xiaoyu.rpc.spring.config.RpcProperties;
import com.xiaoyu.rpc.spring.processor.RpcPostProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RPC 自动配置类
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(RpcProperties.class)
public class RpcAutoConfiguration {

    /**
     * 将 Spring Boot 配置同步到 RpcConfig (核心框架配置)
     */
    @Bean
    public RpcConfig rpcConfig(RpcProperties properties) {
        // 通过 System Properties 传递配置，让 RpcConfig 能够读取
        System.setProperty("rpc.transport", properties.getTransport());
        System.setProperty("rpc.protocol", properties.getProtocol());
        System.setProperty("rpc.server-host", properties.getServerHost());
        System.setProperty("rpc.server-port", String.valueOf(properties.getServerPort()));
        System.setProperty("rpc.registry", properties.getRegistry());
        System.setProperty("rpc.registry-address", properties.getRegistryAddress());
        System.setProperty("rpc.serializer", properties.getSerializer());
        System.setProperty("rpc.proxy", properties.getProxy());
        System.setProperty("rpc.load-balancer", properties.getLoadBalancer());

        log.info("RPC 配置已从 Spring Boot 同步: registry={}, port={}",
                properties.getRegistry(), properties.getServerPort());

        return RpcConfig.getInstance();
    }

    /**
     * 创建 RpcServer Bean (仅当 serverEnabled=true 时)
     */
    @Bean
    @ConditionalOnProperty(prefix = "rpc", name = "server-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public RpcServer rpcServer(RpcConfig rpcConfig) {
        log.info("创建 RpcServer Bean");
        return new RpcServer();
    }

    /**
     * 创建 RPC Bean 后处理器
     */
    @Bean
    public RpcPostProcessor rpcPostProcessor() {
        return new RpcPostProcessor();
    }

    /**
     * 启动 RpcServer
     */
    @Bean
    @ConditionalOnProperty(prefix = "rpc", name = "server-enabled", havingValue = "true", matchIfMissing = true)
    public RpcServerRunner rpcServerRunner(RpcServer rpcServer) {
        return new RpcServerRunner(rpcServer);
    }

    /**
     * 使用 CommandLineRunner 启动 RpcServer
     */
    @Slf4j
    public static class RpcServerRunner implements org.springframework.boot.CommandLineRunner {
        private final RpcServer rpcServer;

        public RpcServerRunner(RpcServer rpcServer) {
            this.rpcServer = rpcServer;
        }

        @Override
        public void run(String... args) throws Exception {
            log.info("启动 RPC Server...");
            // 在新线程中启动，避免阻塞 Spring Boot 主线程
            Thread serverThread = new Thread(() -> {
                try {
                    rpcServer.start();
                } catch (InterruptedException e) {
                    log.error("RPC Server 启动失败", e);
                    Thread.currentThread().interrupt();
                }
            }, "rpc-server-thread");
            serverThread.setDaemon(true);
            serverThread.start();
        }
    }
}
