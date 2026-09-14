package com.xiaoyu.rpc.core.client;

import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.core.config.RpcConfig;

public class RpcClientProxy {

    private RpcClientProxy() {
    }

    public static <T> T create(Class<T> clazz) {
        // 代理类型由配置决定（jdk / bytebuddy 等），这里统一走 SPI 扩展点加载
        return currentProxyFactory().getProxy(clazz);
    }

    /**
     * 释放当前代理工厂持有的客户端网络资源。
     * 该方法面向应用退出阶段，调用后当前 SPI 代理工厂不再接受新的 RPC 调用。
     */
    public static void shutdown() {
        currentProxyFactory().close();
    }

    private static ProxyFactory currentProxyFactory() {
        String proxyType = RpcConfig.getInstance().getProxyType();
        return ExtensionLoader.getExtensionLoader(ProxyFactory.class).getExtension(proxyType);
    }
}
