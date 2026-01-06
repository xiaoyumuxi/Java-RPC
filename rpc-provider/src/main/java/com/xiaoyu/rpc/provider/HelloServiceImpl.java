package com.xiaoyu.rpc.provider;

import com.xiaoyu.rpc.api.HelloService;

public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String name) {
        return "Hello, " + name + "! (from Multi-Module Netty Server)";
    }
}
