package com.xiaoyu.rpc.core.client;

import com.xiaoyu.rpc.common.vo.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

// 这是一个 Netty 的 Handler，专门负责“收信”
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 这是一个 Netty 的 Handler，专门负责“收信”
public class NettyRpcClientHandler extends SimpleChannelInboundHandler<RpcResponse> {
    private static final Logger log = LoggerFactory.getLogger(NettyRpcClientHandler.class);

    private CompletableFuture<Object> future;

    public void setFuture(CompletableFuture<Object> future) {
        this.future = future;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponse response) {
        // 【关键修复点】
        // 之前你写的是 future.complete(response.getData()); 导致传回去的是 ByteString
        // 现在我们把整个 response 对象传回去，让 Proxy 去判断状态和拆包
        log.info("客户端收到响应状态: {}", response.getMessage());
        future.complete(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    public CompletableFuture<Object> getFuture() {
        return future;
    }
}