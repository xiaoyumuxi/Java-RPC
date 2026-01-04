package Serialization;

import extension.ExtensionLoader;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SerializerCode {

    public static final byte JAVA_SERIALIZER = 0x01;
    public static final byte KRYO_SERIALIZER = 0x02;
    public static final byte PROTOBUF_SERIALIZER = 0x03;

    private static final Map<Byte, Serializer> codeMap = new ConcurrentHashMap<>();
    private static final Map<String, Serializer> nameMap = new ConcurrentHashMap<>();

    // 静态初始化块，通过 SPI 加载所有 Serializer
    static {
        ExtensionLoader<Serializer> loader = ExtensionLoader.getExtensionLoader(Serializer.class);
        // 获取所有 SPI 定义的 key (例如 "java", "kryo")
        for (String name : loader.getSupportedExtensions()) {
            try {
                // 加载实例
                Serializer serializer = loader.getExtension(name);
                // 放入缓存 Map
                codeMap.put(serializer.getCode(), serializer);
                nameMap.put(name, serializer);
                log.info("Loaded SPI Serializer: name={}, code={}", name, serializer.getCode());

            } catch (Exception e) {
                log.error("Failed to load SPI serializer with name: " + name, e);
            }
        }
    }

    /**
     * 根据 code 获取实例 (供 Decoder 使用)
     */
    public static Serializer getSerializerByCode(byte code) {
        Serializer serializer = codeMap.get(code);
        if (serializer == null) {
            log.warn("无法找到 code={} 的序列化器", code);
            throw new RuntimeException("没有找到对应的序列化器，code=" + code);
        }
        return serializer;
    }

    /**
     * 根据 name 获取实例 (供 Config 使用)
     */
    public static Serializer getSerializerByName(String name) {
        Serializer serializer = nameMap.get(name);
        if (serializer == null) {
            // 尝试使用全小写再次查找 (兼容性处理)
            serializer = nameMap.get(name.toLowerCase());
        }

        if (serializer == null) {
            // 尝试直接 SPI 加载
            try {
                serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension(name);
                if (serializer != null) {
                    codeMap.put(serializer.getCode(), serializer);
                    nameMap.put(name, serializer);
                    return serializer;
                }
            } catch (Exception e) {
                // ignore
            }
            throw new RuntimeException("没有找到对应的序列化器: " + name);
        }
        return serializer;
    }
}
