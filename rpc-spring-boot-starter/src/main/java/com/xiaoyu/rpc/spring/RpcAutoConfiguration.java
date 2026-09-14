package com.xiaoyu.rpc.spring;

import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.server.RpcServer;
import com.xiaoyu.rpc.spring.config.RpcProperties;
import com.xiaoyu.rpc.spring.processor.RpcPostProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RPC 自动配置类。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(RpcProperties.class)
public class RpcAutoConfiguration {

    @Bean
    public RpcConfig rpcConfig(RpcProperties properties) {
        // 核心模块不依赖 Spring，通过 System Properties 作为两层之间的配置桥接。
        System.setProperty("rpc.transport", properties.getTransport());
        System.setProperty("rpc.protocol", properties.getProtocol());
        System.setProperty("rpc.server-host", properties.getServerHost());
        System.setProperty("rpc.server-port", String.valueOf(properties.getServerPort()));
        System.setProperty("rpc.registry", properties.getRegistry());
        System.setProperty("rpc.registry-address", properties.getRegistryAddress());
        System.setProperty("rpc.serializer", properties.getSerializer());
        System.setProperty("rpc.proxy", properties.getProxy());
        System.setProperty("rpc.load-balancer", properties.getLoadBalancer());
        System.setProperty("rpc.max-message-size", String.valueOf(properties.getMaxMessageSize()));
        System.setProperty("rpc.request-timeout-ms", String.valueOf(properties.getRequestTimeoutMs()));
        System.setProperty("rpc.worker-threads", String.valueOf(properties.getWorkerThreads()));
        System.setProperty("rpc.boss-threads", String.valueOf(properties.getBossThreads()));
        System.setProperty("rpc.business-threads", String.valueOf(properties.getBusinessThreads()));
        System.setProperty("rpc.business-queue-capacity", String.valueOf(properties.getBusinessQueueCapacity()));
        System.setProperty("rpc.max-connections", String.valueOf(properties.getMaxConnections()));

        log.info("RPC 配置已从 Spring Boot 同步: registry={}, server={}:{}, protocol={}, requestTimeoutMs={}",
                properties.getRegistry(), properties.getServerHost(), properties.getServerPort(),
                properties.getProtocol(), properties.getRequestTimeoutMs());
        return RpcConfig.getInstance();
    }

    @Bean
    @ConditionalOnProperty(prefix = "rpc", name = "server-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public RpcServer rpcServer(RpcConfig rpcConfig) {
        log.info("创建 RpcServer Bean");
        return new RpcServer();
    }

    @Bean
    public RpcPostProcessor rpcPostProcessor() {
        return new RpcPostProcessor();
    }

    @Bean
    @ConditionalOnProperty(prefix = "rpc", name = "server-enabled", havingValue = "true", matchIfMissing = true)
    public RpcServerLifecycle rpcServerLifecycle(RpcServer rpcServer) {
        return new RpcServerLifecycle(rpcServer);
    }

    /**
     * 将 RPC Server 纳入 Spring 生命周期：Context 启动时同步启动，关闭时优雅释放服务端资源。
     */
    public static class RpcServerLifecycle implements SmartLifecycle {
        private final RpcServer rpcServer;
        private final AtomicBoolean running = new AtomicBoolean(false);

        public RpcServerLifecycle(RpcServer rpcServer) {
            this.rpcServer = rpcServer;
        }

        @Override
        public void start() {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            try {
                rpcServer.start();
                log.info("RPC Server 已由 Spring SmartLifecycle 启动");
            } catch (InterruptedException e) {
                running.set(false);
                Thread.currentThread().interrupt();
                throw new IllegalStateException("RPC Server 启动被中断", e);
            } catch (RuntimeException e) {
                running.set(false);
                throw e;
            }
        }

        @Override
        public void stop() {
            if (!running.getAndSet(false)) {
                return;
            }
            rpcServer.close();
            log.info("RPC Server 已由 Spring SmartLifecycle 停止");
        }

        @Override
        public boolean isRunning() {
            return running.get();
        }

        @Override
        public boolean isAutoStartup() {
            return true;
        }

        @Override
        public int getPhase() {
            // 启动尽量靠后，关闭尽量靠前，先摘除 RPC 流量再销毁其他业务 Bean。
            return Integer.MAX_VALUE;
        }
    }
}
