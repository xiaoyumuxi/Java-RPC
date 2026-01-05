package client;

import extension.SPI;

@SPI
public interface ProxyFactory {
    <T> T getProxy(Class<T> clazz);
}
