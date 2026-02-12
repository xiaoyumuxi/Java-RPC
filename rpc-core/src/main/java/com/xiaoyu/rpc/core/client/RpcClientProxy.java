package com.xiaoyu.rpc.core.client;

import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.common.extension.ExtensionLoader;

public class RpcClientProxy {

    public static <T> T create(Class<T> clazz) {
        // 代理类型由配置决定（jdk / bytebuddy 等），这里统一走 SPI 扩展点加载
        String proxyType = RpcConfig.getInstance().getProxyType();
        ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getExtension(proxyType);
        return proxyFactory.getProxy(clazz);
    }
}
