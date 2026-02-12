package com.xiaoyu.rpc.consumer;

import com.xiaoyu.rpc.api.HelloService;
import com.xiaoyu.rpc.core.client.RpcClientProxy;

public class ConsumerApp {
    public static void main(String[] args) {
        try {
            // Use configuration from rpc-config.yaml (default: nacos)
            // System.setProperty("rpc.registry", "local");

            System.out.println("Starting Client...");
            HelloService helloService = RpcClientProxy.create(HelloService.class);

            System.out.println(">>> First Call");
            String result1 = helloService.sayHello("World1");
            System.out.println("Result1: " + result1);

            System.out.println(">>> Second Call");
            String result2 = helloService.sayHello("World2");
            System.out.println("Result2: " + result2);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }
}
