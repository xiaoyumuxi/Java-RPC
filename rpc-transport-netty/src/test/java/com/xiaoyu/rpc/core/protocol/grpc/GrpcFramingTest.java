package com.xiaoyu.rpc.core.protocol.grpc;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import com.xiaoyu.rpc.core.client.NettyRpcClientHandler;
import com.xiaoyu.rpc.core.client.RpcStreamResponseHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class GrpcFramingTest {
    @Test
    void serverAccumulatesSplitPrefixAndLargeMessage() {
        RpcRequest request = RpcRequest.newBuilder().setRequestId("large")
                .addParameters(ByteString.copyFrom(new byte[256 * 1024])).build();
        ByteBuf wire = framed(request.toByteArray());
        EmbeddedChannel server = new EmbeddedChannel(new GrpcServerHandler(null));
        try {
            server.writeInbound(new DefaultHttp2DataFrame(wire.readRetainedSlice(2), false));
            assertNull(server.readInbound());
            while (wire.readableBytes() > 8192) {
                server.writeInbound(new DefaultHttp2DataFrame(wire.readRetainedSlice(8192), false));
                assertNull(server.readInbound());
            }
            server.writeInbound(new DefaultHttp2DataFrame(wire.readRetainedSlice(wire.readableBytes()), true));
            assertEquals(request, server.readInbound());
            assertNull(server.readInbound());
        } finally {
            wire.release();
            server.finishAndReleaseAll();
        }
    }

    @Test
    void clientAccumulatesLargeResponseAndWaitsForTrailers() {
        NettyRpcClientHandler shared = new NettyRpcClientHandler();
        CompletableFuture<Object> future = new CompletableFuture<>();
        shared.addFuture("large", future);
        RpcResponse response = RpcResponse.newBuilder().setRequestId("large")
                .setData(ByteString.copyFrom(new byte[256 * 1024])).build();
        EmbeddedChannel channel = new EmbeddedChannel(new GrpcClientResponseHandler(shared, "large"),
                new RpcStreamResponseHandler(shared, "large"));
        ByteBuf wire = framed(response.toByteArray());
        try {
            channel.writeInbound(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().status("200"), false));
            channel.writeInbound(new DefaultHttp2DataFrame(wire.readRetainedSlice(3), false));
            while (wire.isReadable()) {
                channel.writeInbound(new DefaultHttp2DataFrame(
                        wire.readRetainedSlice(Math.min(8192, wire.readableBytes())), false));
            }
            assertFalse(future.isDone(), "A body alone is not gRPC success");
            channel.writeInbound(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().set("grpc-status", "0"), true));
            assertEquals(response, future.join());
            channel.close();
            assertFalse(future.isCompletedExceptionally());
        } finally {
            wire.release();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void errorTrailersDoNotBecomeSuccessfulResponse() {
        NettyRpcClientHandler shared = new NettyRpcClientHandler();
        CompletableFuture<Object> future = new CompletableFuture<>();
        shared.addFuture("error", future);
        EmbeddedChannel channel = new EmbeddedChannel(new GrpcClientResponseHandler(shared, "error"),
                new RpcStreamResponseHandler(shared, "error"));
        try {
            channel.writeInbound(new DefaultHttp2DataFrame(framed(RpcResponse.newBuilder().setRequestId("error").build().toByteArray()), false));
            channel.writeInbound(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().set("grpc-status", "13"), true));
            assertTrue(future.isCompletedExceptionally());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void boundsAndTruncatedMessagesAreRejected() {
        try (GrpcMessageAccumulator accumulator = new GrpcMessageAccumulator(32)) {
            ByteBuf header = Unpooled.buffer().writeByte(0).writeInt(33);
            try {
                assertThrows(TooLongFrameException.class,
                        () -> accumulator.append(UnpooledByteBufAllocator.DEFAULT, header));
            } finally {
                header.release();
            }
        }
        try (GrpcMessageAccumulator accumulator = new GrpcMessageAccumulator(32)) {
            ByteBuf truncated = Unpooled.buffer().writeByte(0).writeInt(8).writeByte(1);
            try {
                assertNull(accumulator.append(UnpooledByteBufAllocator.DEFAULT, truncated));
                assertThrows(CorruptedFrameException.class, accumulator::requireComplete);
            } finally {
                truncated.release();
            }
        }
    }

    private static ByteBuf framed(byte[] payload) {
        return Unpooled.buffer(payload.length + 5).writeByte(0).writeInt(payload.length).writeBytes(payload);
    }
}
