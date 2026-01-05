package config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

/**
 * RPC配置类 - 从YAML文件读取配置
 */
@Data
@Slf4j
public class RpcConfig {
    private static RpcConfig instance;

    // 序列化类型: JAVA, KRYO, PROTOBUF
    private String serializerType;

    // 服务端口
    private Integer serverPort;

    // 服务端地址
    private String serverHost;
    // 协议名称
    private String protocol;
    // 注册中心地址
    private String registryAddress;
    // 注册中心类型
    private String registryType = "nacos";
    // 代理类型
    private String proxyType = "jdk";

    private RpcConfig() {
        loadConfig();
    }

    /**
     * 获取单例实例
     */
    public static synchronized RpcConfig getInstance() {
        if (instance == null) {
            instance = new RpcConfig();
        }
        return instance;
    }

    /**
     * 从YAML配置文件加载配置
     */
    private void loadConfig() {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = RpcConfig.class.getClassLoader()
                .getResourceAsStream("rpc-config.yaml")) {

            if (inputStream == null) {
                log.warn("未找到 rpc-config.yaml 文件，使用默认配置");
                setDefaultConfig();
                return;
            }

            Map<String, Object> config = yaml.load(inputStream);

            // 读取 rpc 配置节点
            if (config != null && config.containsKey("rpc")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rpcConfig = (Map<String, Object>) config.get("rpc");

                this.serializerType = (String) rpcConfig.getOrDefault("serializer", "PROTOBUF");
                this.serverPort = (Integer) rpcConfig.getOrDefault("server-port", 8080);
                this.serverHost = (String) rpcConfig.getOrDefault("server-host", "127.0.0.1");
                this.protocol = (String) rpcConfig.getOrDefault("protocol", "netty");
                this.registryAddress = (String) rpcConfig.getOrDefault("registry-address", "127.0.0.1:8848");
                this.registryType = (String) rpcConfig.getOrDefault("registry", "nacos");
                this.proxyType = (String) rpcConfig.getOrDefault("proxy", "jdk");

                log.info("配置加载成功: 序列化方式={}, 服务器={}:{},使用的协议={}, 注册中心={}, 代理方式={}",
                        serializerType, serverHost, serverPort, protocol, registryAddress, proxyType);
            } else {
                log.warn("配置文件格式错误，使用默认配置");
                setDefaultConfig();
            }

        } catch (Exception e) {
            log.error("加载配置文件失败，使用默认配置", e);
            setDefaultConfig();
        }

        // --- 支持 System Properties (-D 选项) 覆盖 ---
        String portStr = System.getProperty("rpc.server-port");
        if (portStr != null) {
            this.serverPort = Integer.parseInt(portStr);
            log.info("检测到 System Property 覆盖端口: {}", this.serverPort);
        }
    }

    /**
     * 设置默认配置
     */
    private void setDefaultConfig() {
        this.serializerType = "PROTOBUF";
        this.serverPort = 8080;
        this.serverHost = "127.0.0.1";
        this.protocol = "netty";
    }

    /**
     * 获取序列化器的字节码
     */
    public byte getSerializerCode() {
        return Serialization.SerializerCode.getSerializerByName(serializerType.toLowerCase()).getCode();
    }
}
