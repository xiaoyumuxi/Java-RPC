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
        // 1. 校验魔数,4Bytes
        int magic = in.readInt();
        log.info("正在解码数据...魔数为：{}", magic);
        if (magic != MAGIC_NUMBER) {
            throw new RuntimeException("未知协议魔数: " + magic);
        }

        // 2. 读取消息类型,1Byte
        byte packageType = in.readByte();
        log.info("正在解码数据...消息类型为：{}", packageType);

        // 3. 读取序列化器标识并获取实例,1Bytes
        byte serializerCode = in.readByte();
        Serializer serializer = SerializerCode.getSerializerByCode(serializerCode);
        log.info("正在解码数据...序列化器标识为：{}", serializerCode);

        // 4. 读取 Body 长度
        int length = in.readInt();
        int maxFrameSize = com.xiaoyu.rpc.core.config.RpcConfig.getInstance().getMaxMessageSize();
        if (length > maxFrameSize || length < 0) {
            log.error("拒绝过大的报文或非法长度: {} bytes, 远程地址: {}", length, ctx.channel().remoteAddress());
            ctx.close(); // 直接断开物理连接，防止持续攻击
            throw new RuntimeException("拒绝过大的报文: " + length);
        }

        // 5. 读取 Body 数据
        byte[] body = new byte[length];
        in.readBytes(body);

        // 6. 反序列化
        Class<?> clazz = (packageType == 0x01) ? RpcRequest.class : RpcResponse.class;
        Object obj = serializer.deserialize(body, clazz);
        out.add(obj);
    }
}