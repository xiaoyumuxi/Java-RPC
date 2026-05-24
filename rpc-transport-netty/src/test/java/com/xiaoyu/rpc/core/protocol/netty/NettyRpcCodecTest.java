package com.xiaoyu.rpc.core.protocol.netty;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("自定义 Netty 协议编解码测试")
public class NettyRpcCodecTest {

    @Test
    @DisplayName("请求报文编码后可被解码还原")
    void testRoundTripRequest() {
        Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension("java");
        EmbeddedChannel encoderChannel = new EmbeddedChannel(new NettyRpcEncoder(serializer));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new NettyRpcDecoder(RpcRequest.class));

        RpcRequest request = RpcRequest.newBuilder()
                .setInterfaceName("com.example.DemoService")
                .setMethodName("echo")
                .addParamTypes("java.lang.String")
                .addParameters(ByteString.copyFrom(serializer.serialize("abc")))
                .build();

        assertTrue(encoderChannel.writeOutbound(request));
        ByteBuf encoded = encoderChannel.readOutbound();
        assertTrue(decoderChannel.writeInbound(encoded));

        Object decoded = decoderChannel.readInbound();
        assertInstanceOf(RpcRequest.class, decoded);
        assertEquals("echo", ((RpcRequest) decoded).getMethodName());
    }

    @Test
    @DisplayName("非法魔数应抛异常")
    void testInvalidMagicNumber() {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyRpcDecoder(RpcRequest.class));
        ByteBuf invalid = Unpooled.buffer();
        invalid.writeInt(0x11223344);
        invalid.writeByte(0x01);
        invalid.writeByte(SerializerCode.JAVA_SERIALIZER);
        invalid.writeInt(0);

        assertThrows(RuntimeException.class, () -> {
            channel.writeInbound(invalid);
            channel.checkException();
        });
    }

    @Test
    @DisplayName("超长消息应被拒绝并关闭连接")
    void testOversizedFrameRejected() {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyRpcDecoder(RpcRequest.class));
        ByteBuf invalid = Unpooled.buffer();
        invalid.writeInt(0xAABBCCDD);
        invalid.writeByte(0x01);
        invalid.writeByte(SerializerCode.JAVA_SERIALIZER);
        invalid.writeInt(100 * 1024 * 1024);

        assertThrows(RuntimeException.class, () -> {
            channel.writeInbound(invalid);
            channel.checkException();
        });
        assertFalse(channel.isOpen(), "Channel should be closed after oversized frame");
    }
}
