package com.xiaoyu.rpc.core.protocol.netty;

import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class MyRpcEncoder extends MessageToByteEncoder<Object> {
    private static final int MAGIC_NUMBER = 0xAABBCCDD; // 魔数
    private final Serializer serializer;

    public MyRpcEncoder(Serializer serializer) {
        this.serializer = serializer;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) {
        // 先写固定魔数（4 字节）
        out.writeInt(MAGIC_NUMBER);

        // 写消息类型（1 字节）
        if (msg instanceof RpcRequest) {
            out.writeByte(0x01); // 请求
        } else {
            out.writeByte(0x02); // 响应
        }

        // 写序列化器标识（1 字节）
        out.writeByte(serializer.getCode());

        // 序列化消息体
        byte[] body = serializer.serialize(msg);

        // 写消息体长度（4 字节）
        out.writeInt(body.length);

        // 写消息体内容
        out.writeBytes(body);
    }
}
