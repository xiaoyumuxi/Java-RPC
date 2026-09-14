package com.xiaoyu.rpc.core.protocol.grpc;

import com.xiaoyu.rpc.common.vo.RpcResponse;
import com.xiaoyu.rpc.core.client.NettyRpcClientHandler;
import com.xiaoyu.rpc.core.config.RpcConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;

/** Decode a unary response and wait for successful gRPC trailers before completing the RPC. */
class GrpcClientResponseHandler extends SimpleChannelInboundHandler<Http2Frame> {
    private final NettyRpcClientHandler clientHandler;
    private final String requestId;
    private final GrpcMessageAccumulator accumulator;
    private RpcResponse response;
    private boolean finished;

    GrpcClientResponseHandler(NettyRpcClientHandler clientHandler, String requestId) {
        this.clientHandler = clientHandler;
        this.requestId = requestId;
        this.accumulator = new GrpcMessageAccumulator(RpcConfig.getInstance().getMaxMessageSize());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Http2Frame frame) throws Exception {
        if (finished) {
            return;
        }
        if (frame instanceof Http2DataFrame data) {
            byte[] payload = accumulator.append(ctx.alloc(), data.content());
            if (payload != null) {
                response = RpcResponse.parseFrom(payload);
            }
            if (data.isEndStream()) {
                throw new CorruptedFrameException("gRPC response ended without status trailers");
            }
        } else if (frame instanceof Http2HeadersFrame headersFrame) {
            Http2Headers headers = headersFrame.headers();
            if (headers.status() != null && !"200".contentEquals(headers.status())) {
                throw new CorruptedFrameException("Unexpected gRPC HTTP status: " + headers.status());
            }
            if (headersFrame.isEndStream()) {
                CharSequence status = headers.get("grpc-status");
                if (status == null || !"0".contentEquals(status)) {
                    throw new CorruptedFrameException("gRPC status=" + status + ", message=" + headers.get("grpc-message"));
                }
                accumulator.requireComplete();
                if (response == null || !requestId.equals(response.getRequestId())) {
                    throw new CorruptedFrameException("Missing response or mismatched gRPC requestId");
                }
                finished = true;
                ctx.fireChannelRead(response);
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        accumulator.close();
        if (!finished) {
            clientHandler.failRequest(requestId, new CorruptedFrameException("gRPC stream closed before successful trailers"));
        }
        super.channelInactive(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        accumulator.close();
        super.handlerRemoved(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        accumulator.close();
        clientHandler.failRequest(requestId, cause);
        ctx.close();
    }
}
