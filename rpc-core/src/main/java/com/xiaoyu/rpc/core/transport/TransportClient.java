package com.xiaoyu.rpc.core.transport;

import com.xiaoyu.rpc.common.vo.RpcRequest;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 * 传输层客户端接口
 */
public interface TransportClient {

    /**
     * 发送 RPC 请求
     *
     * @param request 请求对象
     * @param address 目标地址
     * @return 响应结果 (CompletableFuture)
     */
    CompletableFuture<Object> sendRequest(RpcRequest request, InetSocketAddress address);
}
