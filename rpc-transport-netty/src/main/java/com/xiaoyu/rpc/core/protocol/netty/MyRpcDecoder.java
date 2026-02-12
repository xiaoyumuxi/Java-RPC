package com.xiaoyu.rpc.core.protocol.netty;

import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MyRpcDecoder extends ReplayingDecoder<Void> {
    private static final Logger log = LoggerFactory.getLogger(MyRpcDecoder.class);
    private static final int MAGIC_NUMBER = 0xAABBCCDD;
    private final Class<?> genericClass;

    public MyRpcDecoder(Class<?> genericClass) {
        this.genericClass = genericClass;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // 先校验魔数（4 字节）
        int magic = in.readInt();
        log.info("正在解码数据...魔数为：{}", magic);
        if (magic != MAGIC_NUMBER) {
            throw new RuntimeException("未知协议魔数: " + magic);
        }

        // 读取消息类型（1 字节）
        byte packageType = in.readByte();
        log.info("正在解码数据...消息类型为：{}", packageType);

        // 读取序列化器标识并拿到对应实现（1 字节）
        byte serializerCode = in.readByte();
        Serializer serializer = SerializerCode.getSerializerByCode(serializerCode);
        log.info("正在解码数据...序列化器标识为：{}", serializerCode);

        // 读取消息体长度
        int length = in.readInt();
        int maxFrameSize = com.xiaoyu.rpc.core.config.RpcConfig.getInstance().getMaxMessageSize();
        if (length > maxFrameSize || length < 0) {
            log.error("拒绝过大的报文或非法长度: {} bytes, 远程地址: {}", length, ctx.channel().remoteAddress());
            ctx.close(); // 直接断开物理连接，防止持续攻击
            throw new RuntimeException("拒绝过大的报文: " + length);
        }

        // 按长度读取消息体数据
        byte[] body = new byte[length];
        in.readBytes(body);

        // 反序列化为请求或响应对象
        Class<?> clazz = (packageType == 0x01) ? RpcRequest.class : RpcResponse.class;
        Object obj = serializer.deserialize(body, clazz);
        out.add(obj);
    }
}
