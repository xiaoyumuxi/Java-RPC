package Test;

import protocol.Http.HttpProtocol;
import protocol.Netty.NettyProtocol;
import protocol.Protocol;
import protocol.ProtocolFactory;

public class ProtocolSpiTest {
    public static void main(String[] args) {
        System.out.println("Starting Protocol SPI Test...");

        // 1. Test loading Netty protocol (dynamic SPI or fallback logic)
        Protocol netty = ProtocolFactory.getProtocol("netty");
        System.out.println("Loaded 'netty': " + netty.getClass().getName());
        if (!(netty instanceof NettyProtocol)) {
            throw new RuntimeException("Expected NettyProtocol but got " + netty.getClass().getName());
        }

        // 2. Test loading HTTP protocol
        Protocol http = ProtocolFactory.getProtocol("http");
        System.out.println("Loaded 'http': " + http.getClass().getName());
        if (!(http instanceof HttpProtocol)) {
            throw new RuntimeException("Expected HttpProtocol but got " + http.getClass().getName());
        }

        // 3. Test loading unknown protocol (should fallback to Netty with warning log)
        System.out.println("Testing unknown protocol fallback...");
        Protocol unknown = ProtocolFactory.getProtocol("unknown_proto");
        System.out.println("Loaded 'unknown_proto': " + unknown.getClass().getName());
        if (!(unknown instanceof NettyProtocol)) {
            throw new RuntimeException(
                    "Fallback failed: Expected NettyProtocol but got " + unknown.getClass().getName());
        }

        System.out.println("Protocol SPI Test Passed Successfully!");
    }
}
