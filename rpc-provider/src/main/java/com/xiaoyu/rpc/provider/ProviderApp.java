package com.xiaoyu.rpc.provider;

import com.xiaoyu.rpc.api.HelloService;
import com.xiaoyu.rpc.core.server.RpcServer;

public class ProviderApp {
    public static void main(String[] args) {
        try {
            // Use configuration from rpc-config.yaml (default: nacos)
            // System.setProperty("rpc.registry", "local");

            RpcServer server = new RpcServer();
            server.register(HelloService.class, new HelloServiceImpl());
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
