package com.xiaoyu.rpc.core.protocol.grpc;

import com.xiaoyu.rpc.common.vo.RpcResponse;
import com.xiaoyu.rpc.core.client.NettyRpcClientHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;

/**
 * 将 gRPC/HTTP2 帧转换为内部 RpcResponse，并交给通用客户端处理器完成 requestId 关联。
 */
class GrpcClientResponseHandler extends SimpleChannelInboundHandler<Http2Frame> {

    private final NettyRpcClientHandler clientHandler;
    private final String requestId;

    GrpcClientResponseHandler(NettyRpcClientHandler clientHandler, String requestId) {
        this.clientHandler = clientHandler;
        this.requestId = requestId;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Http2Frame frame) throws Exception {
        if (frame instanceof Http2DataFrame) {
            Http2DataFrame dataFrame = (Http2DataFrame) frame;
            ByteBuf content = dataFrame.content();
            if (content.readableBytes() < 5) {
                clientHandler.failRequest(requestId, new IllegalStateException("Invalid gRPC frame: missing 5-byte prefix"));
                return;
            }

            byte compressedFlag = content.readByte();
            if (compressedFlag != 0) {
                clientHandler.failRequest(requestId, new UnsupportedOperationException("Compressed gRPC payload is not supported"));
                return;
            }

            int length = content.readInt();
            if (content.readableBytes() < length) {
                clientHandler.failRequest(requestId, new IllegalStateException("Invalid gRPC frame: payload length mismatch"));
                return;
            }

            ByteBuf slice = content.readSlice(length);
            RpcResponse response;
            if (slice.nioBufferCount() > 0) {
                response = RpcResponse.parseFrom(slice.nioBuffer());
            } else {
                byte[] bytes = new byte[length];
                slice.readBytes(bytes);
                response = RpcResponse.parseFrom(bytes);
            }
            ctx.fireChannelRead(response);
            return;
        }

        if (frame instanceof Http2HeadersFrame) {
            Http2Headers headers = ((Http2HeadersFrame) frame).headers();
            CharSequence grpcStatus = headers.get("grpc-status");
            if (grpcStatus != null && !"0".contentEquals(grpcStatus)) {
                CharSequence grpcMessage = headers.get("grpc-message");
                String message = grpcMessage == null ? "unknown grpc error" : grpcMessage.toString();
                clientHandler.failRequest(requestId, new RuntimeException("gRPC error status=" + grpcStatus + ", message=" + message));
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        clientHandler.failRequest(requestId, cause);
        ctx.close();
    }
}
