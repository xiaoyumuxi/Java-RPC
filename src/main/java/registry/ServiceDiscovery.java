package registry;

import extension.SPI;

import java.net.InetSocketAddress;

/**
 * 服务发现接口
 */
@SPI
public interface ServiceDiscovery {
    /**
     * 查找服务地址
     *
     * @param serviceName 服务名称
     * @return 服务地址
     */
    InetSocketAddress lookupService(String serviceName);
}
