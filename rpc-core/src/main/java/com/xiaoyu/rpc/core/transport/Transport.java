package com.xiaoyu.rpc.core.transport;

import com.xiaoyu.rpc.common.extension.SPI;

/**
 * 传输层 SPI 接口
 * 用于屏蔽底层网络通信框架 (Netty, Mina, etc.)
 */
@SPI("netty")
public interface Transport {

    /**
     * 创建服务端
     * 
     * @param port 监听端口
     * @return 服务端实例
     */
    TransportServer createServer(int port);

    /**
     * 创建客户端
     * 
     * @return 客户端实例
     */
    TransportClient createClient();
}
