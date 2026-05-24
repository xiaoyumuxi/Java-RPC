package com.xiaoyu.rpc.consumer;

import com.xiaoyu.rpc.core.client.RpcClientProxy;
import com.xiaoyu.rpc.api.HelloService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Http2SimpleTest {
    private static final Logger log = LoggerFactory.getLogger(Http2SimpleTest.class);

    public static void main(String[] args) {
        try {
            // 创建代理对象
            HelloService helloService = RpcClientProxy.create(HelloService.class);

            // 像调用本地方法一样调用远程
            String result = helloService.sayHello("World");

            System.out.println("RPC 调用结果: " + result);
        } catch (Exception e) {
            log.error("RPC 调用失败", e);
        }
    }
}