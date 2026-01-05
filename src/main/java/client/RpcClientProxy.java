package client;

import config.RpcConfig;
import extension.ExtensionLoader;

public class RpcClientProxy {

    public static <T> T create(Class<T> clazz) {
        String proxyType = RpcConfig.getInstance().getProxyType();
        ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getExtension(proxyType);
        return proxyFactory.getProxy(clazz);
    }
}