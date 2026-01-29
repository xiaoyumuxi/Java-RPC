package com.xiaoyu.rpc.benchmark;

import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.common.serialization.Serializer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import com.xiaoyu.rpc.common.vo.RpcRequest;
import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({ Mode.AverageTime, Mode.Throughput })
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 2, time = 1)
public class SerializationBenchmark {

    @Param({ "java", "kryo", "json", "protobuf" })
    private String serializerName;

    private Serializer serializer;
    private RpcRequest rpcRequest;
    private String testString;

    private byte[] serializedRequestBytes;
    private byte[] serializedStringBytes;

    @Setup
    public void setup() {
        serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension(serializerName);

        // Setup String
        testString = "Hello, Benchmark! This is a test string for RPC serialization comparison.";
        try {
            serializedStringBytes = serializer.serialize(testString);
        } catch (Exception e) {
            System.err.println("Serializer [" + serializerName + "] failed to serialize String: " + e.getMessage());
        }

        // Setup RpcRequest (Protobuf Message)
        // Note: Java/Kryo/Json can also serialize this since it implements Serializable
        // (via GeneratedMessageV3)
        // or effectively acts as a POJO for them.
        RpcRequest.Builder builder = RpcRequest.newBuilder()
                .setInterfaceName("com.example.HelloService")
                .setMethodName("sayHello")
                .addParamTypes("java.lang.String");

        // Add dummy bytes parameter
        builder.addParameters(com.google.protobuf.ByteString.copyFromUtf8("Benchmark"));

        rpcRequest = builder.build();

        try {
            serializedRequestBytes = serializer.serialize(rpcRequest);
            System.out.println(
                    "Serializer [" + serializerName + "] POJO (RpcRequest) Size: " + serializedRequestBytes.length
                            + " bytes");
        } catch (Exception e) {
            System.err.println("Serializer [" + serializerName + "] failed to serialize POJO: " + e.getMessage());
        }
    }

    @Benchmark
    public void serializePojo(org.openjdk.jmh.infra.Blackhole bh) {
        bh.consume(serializer.serialize(rpcRequest));
    }

    @Benchmark
    public void deserializePojo(org.openjdk.jmh.infra.Blackhole bh) {
        bh.consume(serializer.deserialize(serializedRequestBytes, RpcRequest.class));
    }

    @Benchmark
    public void serializeString(org.openjdk.jmh.infra.Blackhole bh) {
        bh.consume(serializer.serialize(testString));
    }

    @Benchmark
    public void deserializeString(org.openjdk.jmh.infra.Blackhole bh) {
        bh.consume(serializer.deserialize(serializedStringBytes, String.class));
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(SerializationBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
