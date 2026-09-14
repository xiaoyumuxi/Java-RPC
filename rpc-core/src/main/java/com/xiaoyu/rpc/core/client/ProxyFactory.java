package com.xiaoyu.rpc.core.client;

import com.xiaoyu.rpc.common.extension.SPI;

@SPI
public interface ProxyFactory extends AutoCloseable {
    <T> T getProxy(Class<T> clazz);

    @Override
    default void close() {
        // 默认无资源需要释放；具体代理实现可覆盖。
    }
}
