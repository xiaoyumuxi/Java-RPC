package com.xiaoyu.rpc.core.client;

import com.xiaoyu.rpc.common.vo.RpcResponse;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端响应处理器。
 * 通过 requestId 将响应路由回对应的 CompletableFuture。
 */
@ChannelHandler.Sharable
public class NettyRpcClientHandler extends SimpleChannelInboundHandler<RpcResponse> {
    private static final Logger log = LoggerFactory.getLogger(NettyRpcClientHandler.class);

    private final Map<String, CompletableFuture<Object>> pendingRequests = new ConcurrentHashMap<>();

    public void addFuture(String requestId, CompletableFuture<Object> future) {
        pendingRequests.put(requestId, future);
    }

    public void removeFuture(String requestId) {
        pendingRequests.remove(requestId);
    }

    public void failRequest(String requestId, Throwable cause) {
        CompletableFuture<Object> future = pendingRequests.remove(requestId);
        if (future != null) {
            future.completeExceptionally(cause);
        }
    }

    public void failAll(Throwable cause) {
        pendingRequests.forEach((requestId, future) -> {
            if (pendingRequests.remove(requestId, future)) {
                future.completeExceptionally(cause);
            }
        });
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponse response) {
        String requestId = response.getRequestId();
        CompletableFuture<Object> future = pendingRequests.remove(requestId);

        if (future != null) {
            // 成功响应属于高频路径，避免 INFO 级逐请求日志影响吞吐和延迟观测。
            log.debug("Client received response for requestId: {}, status: {}", requestId, response.getMessage());
            future.complete(response);
        } else {
            log.warn("Client received response for unknown or timed-out requestId: {}", requestId);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        failAll(new ClosedChannelException());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Client caught exception", cause);
        failAll(cause);
        ctx.close();
    }

    int pendingRequestCount() {
        return pendingRequests.size();
    }
}
