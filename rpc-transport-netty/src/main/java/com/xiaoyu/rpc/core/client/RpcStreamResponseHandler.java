package com.xiaoyu.rpc.core.client;

import com.xiaoyu.rpc.common.vo.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.util.ReferenceCountUtil;

import java.nio.channels.ClosedChannelException;

/**
 * One handler per HTTP/2 stream. Never install the connection-scoped pending-request
 * handler on a child channel: closing that stream must not fail sibling requests.
 */
public final class RpcStreamResponseHandler extends SimpleChannelInboundHandler<RpcResponse> {
    private final NettyRpcClientHandler connectionHandler;
    private final String requestId;

    public RpcStreamResponseHandler(NettyRpcClientHandler connectionHandler, String requestId) {
        this.connectionHandler = connectionHandler;
        this.requestId = requestId;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponse response) throws Exception {
        if (!requestId.equals(response.getRequestId())) {
            exceptionCaught(ctx, new IllegalStateException("Response requestId does not match its HTTP/2 stream"));
            return;
        }
        // Invoke only the response-routing operation, not connection lifecycle callbacks.
        connectionHandler.channelRead(ctx, response);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        connectionHandler.failRequest(requestId, new ClosedChannelException());
        super.channelInactive(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        if (event instanceof Http2ResetFrame reset) {
            try {
                exceptionCaught(ctx, new IllegalStateException("HTTP/2 stream reset: " + reset.errorCode()));
            } finally {
                ReferenceCountUtil.release(event);
            }
        } else {
            super.userEventTriggered(ctx, event);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        connectionHandler.failRequest(requestId, cause);
        ctx.close();
    }
}
