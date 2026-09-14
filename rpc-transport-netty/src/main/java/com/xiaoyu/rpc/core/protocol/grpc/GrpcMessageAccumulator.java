package com.xiaoyu.rpc.core.protocol.grpc;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.TooLongFrameException;

/** Bounded, unary gRPC deframer. HTTP/2 DATA boundaries are not message boundaries. */
final class GrpcMessageAccumulator implements AutoCloseable {
    private final int maxMessageSize;
    private ByteBuf buffer;
    private boolean complete;

    GrpcMessageAccumulator(int maxMessageSize) {
        if (maxMessageSize < 1 || maxMessageSize > Integer.MAX_VALUE - 5) {
            throw new IllegalArgumentException("Invalid gRPC maximum message size");
        }
        this.maxMessageSize = maxMessageSize;
    }

    byte[] append(ByteBufAllocator allocator, ByteBuf fragment) {
        int size = fragment.readableBytes();
        if (size == 0) {
            return null;
        }
        if (complete) {
            throw new CorruptedFrameException("Multiple messages on a unary gRPC stream");
        }
        if (buffer == null) {
            buffer = allocator.buffer(Math.min(256, maxMessageSize + 5), maxMessageSize + 5);
        }
        if (size > maxMessageSize + 5 - buffer.readableBytes()) {
            throw new TooLongFrameException("gRPC message exceeds configured maximum");
        }
        buffer.writeBytes(fragment, fragment.readerIndex(), size);
        if (buffer.readableBytes() < 5) {
            return null;
        }
        if (buffer.getUnsignedByte(0) != 0) {
            throw new CorruptedFrameException("Compressed gRPC messages are not supported");
        }
        int length = buffer.getInt(1);
        if (length < 0 || length > maxMessageSize) {
            throw new TooLongFrameException("Invalid gRPC message length: " + length);
        }
        if (buffer.readableBytes() > length + 5) {
            throw new CorruptedFrameException("Extra bytes after unary gRPC message");
        }
        if (buffer.readableBytes() < length + 5) {
            return null;
        }
        byte[] payload = new byte[length];
        buffer.getBytes(5, payload);
        complete = true;
        close();
        return payload;
    }

    void requireComplete() {
        if (!complete) {
            throw new CorruptedFrameException("gRPC stream ended with a missing or truncated message");
        }
    }

    @Override
    public void close() {
        if (buffer != null) {
            buffer.release();
            buffer = null;
        }
    }
}
