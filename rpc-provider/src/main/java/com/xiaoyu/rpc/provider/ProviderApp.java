package com.xiaoyu.rpc.provider;

import com.xiaoyu.rpc.api.HelloService;
import com.xiaoyu.rpc.core.server.RpcServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProviderApp {
    private static final Logger log = LoggerFactory.getLogger(ProviderApp.class);

    public static void main(String[] args) {
        try {
            // Use configuration from rpc-config.yaml (default: nacos)
            // System.setProperty("rpc.registry", "local");

            RpcServer server = new RpcServer();
            server.register(HelloService.class, new HelloServiceImpl());
            server.start();
        } catch (Exception e) {
            log.error("Failed to start RPC provider", e);
            System.exit(1);
        }
    }
}
