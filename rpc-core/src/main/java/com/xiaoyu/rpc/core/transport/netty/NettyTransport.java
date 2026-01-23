package com.xiaoyu.rpc.core.transport.netty;

import com.xiaoyu.rpc.core.transport.Transport;
import com.xiaoyu.rpc.core.transport.TransportClient;
import com.xiaoyu.rpc.core.transport.TransportServer;

/**
 * Netty 传输层实现
 */
public class NettyTransport implements Transport {

    @Override
    public TransportServer createServer(int port) {
        return new NettyTransportServer(port);
    }

    @Override
    public TransportClient createClient() {
        return new NettyTransportClient();
    }
}
