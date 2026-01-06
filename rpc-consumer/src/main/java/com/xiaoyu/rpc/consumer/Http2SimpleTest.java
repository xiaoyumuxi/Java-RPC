package com.xiaoyu.rpc.consumer;

import com.xiaoyu.rpc.core.client.RpcClientProxy;
import com.xiaoyu.rpc.api.HelloService;

public class Http2SimpleTest {
    public static void main(String[] args) {
        try {
            // 创建代理对象
            HelloService helloService = RpcClientProxy.create(HelloService.class);

            // 像调用本地方法一样调用远程
            String result = helloService.sayHello("World");

            System.out.println("RPC 调用结果: " + result);
        } catch (Exception e) {
            System.err.println("RPC 调用失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}