package com.xiaoyu.rpc.core.protocol;

import com.xiaoyu.rpc.core.protocol.http.HttpProtocol;
import com.xiaoyu.rpc.core.protocol.netty.NettyProtocol;
import com.xiaoyu.rpc.core.protocol.http2.Http2Protocol;
import com.xiaoyu.rpc.core.protocol.grpc.GrpcProtocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 协议 SPI 单元测试
 */
@DisplayName("Protocol 协议测试")
public class ProtocolTest {

    @Test
    @DisplayName("测试 Netty 协议加载")
    void testNettyProtocolLoading() {
        Protocol protocol = ProtocolFactory.getProtocol("netty");

        assertNotNull(protocol, "Netty protocol should be loaded");
        assertTrue(protocol instanceof NettyProtocol, "Should be instance of NettyProtocol");
    }

    @Test
    @DisplayName("测试 HTTP 协议加载")
    void testHttpProtocolLoading() {
        Protocol protocol = ProtocolFactory.getProtocol("http");

        assertNotNull(protocol, "HTTP protocol should be loaded");
        assertTrue(protocol instanceof HttpProtocol, "Should be instance of HttpProtocol");
    }

    @Test
    @DisplayName("测试 HTTP2 协议加载")
    void testHttp2ProtocolLoading() {
        Protocol protocol = ProtocolFactory.getProtocol("http2");

        assertNotNull(protocol, "HTTP2 protocol should be loaded");
        assertTrue(protocol instanceof Http2Protocol, "Should be instance of Http2Protocol");
    }

    @Test
    @DisplayName("测试 gRPC 协议加载")
    void testGrpcProtocolLoading() {
        Protocol protocol = ProtocolFactory.getProtocol("grpc");

        assertNotNull(protocol, "gRPC protocol should be loaded");
        assertTrue(protocol instanceof GrpcProtocol, "Should be instance of GrpcProtocol");
    }

    @Test
    @DisplayName("测试未知协议回退到 Netty")
    void testUnknownProtocolFallback() {
        Protocol protocol = ProtocolFactory.getProtocol("unknown_protocol");

        assertNotNull(protocol, "Unknown protocol should fallback");
        assertTrue(protocol instanceof NettyProtocol, "Should fallback to NettyProtocol");
    }

    @Test
    @DisplayName("测试空协议名默认回退到 Netty")
    void testNullProtocolFallback() {
        Protocol protocol = ProtocolFactory.getProtocol(null);

        assertNotNull(protocol, "Null protocol should fallback");
        assertTrue(protocol instanceof NettyProtocol, "Null protocol should fallback to NettyProtocol");
    }

    @Test
    @DisplayName("测试空白协议名默认回退到 Netty")
    void testBlankProtocolFallback() {
        Protocol protocol = ProtocolFactory.getProtocol("   ");

        assertNotNull(protocol, "Blank protocol should fallback");
        assertTrue(protocol instanceof NettyProtocol, "Blank protocol should fallback to NettyProtocol");
    }

    @Test
    @DisplayName("测试 ProtocolFactory 多次获取相同协议")
    void testProtocolCaching() {
        Protocol first = ProtocolFactory.getProtocol("netty");
        Protocol second = ProtocolFactory.getProtocol("netty");

        assertNotNull(first, "First should not be null");
        assertNotNull(second, "Second should not be null");
        // 由于 SPI ExtensionLoader 使用缓存，两次获取应该是同一实例
        assertSame(first, second, "Same protocol should return same instance");
    }
}
