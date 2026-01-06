package com.xiaoyu.rpc.core.client;

import com.xiaoyu.rpc.common.extension.SPI;

@SPI
public interface ProxyFactory {
    <T> T getProxy(Class<T> clazz);
}
