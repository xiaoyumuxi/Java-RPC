package Serialization;

import VO.RpcRequest;
import VO.RpcResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class MyRpcDecoder extends ReplayingDecoder<Void> {
    private static final int MAGIC_NUMBER = 0xAABBCCDD;

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

        // 5. 读取 Body 数据
        byte[] body = new byte[length];
        in.readBytes(body);

        // 6. 反序列化
        Class<?> clazz = (packageType == 0x01) ? RpcRequest.class : RpcResponse.class;
        Object obj = serializer.deserialize(body, clazz);
        out.add(obj);
    }
}