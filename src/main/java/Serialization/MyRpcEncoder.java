package Serialization;

import VO.RpcRequest;
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
        // 1. 写入魔数 (4字节)
        out.writeInt(MAGIC_NUMBER);

        // 2. 写入消息类型 (1字节)
        if (msg instanceof RpcRequest) {
            out.writeByte(0x01); // 请求
        } else {
            out.writeByte(0x02); // 响应
        }

        // 3. 写入序列化器标识 (1字节)
        out.writeByte(serializer.getCode());

        // 4. 获取序列化后的字节数组
        byte[] body = serializer.serialize(msg);

        // 5. 写入 Body 长度 (4字节)
        out.writeInt(body.length);

        // 6. 写入 Body 数据
        out.writeBytes(body);
    }
}