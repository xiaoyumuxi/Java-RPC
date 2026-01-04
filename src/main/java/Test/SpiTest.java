package Test;

import Serialization.Serializer;
import Serialization.SerializerCode;
import extension.ExtensionLoader;

public class SpiTest {
    public static void main(String[] args) {
        System.out.println("Beginning SPI Test...");

        // 1. Test ExtensionLoader directly
        System.out.println("\n--- Testing ExtensionLoader ---");
        ExtensionLoader<Serializer> loader = ExtensionLoader.getExtensionLoader(Serializer.class);
        System.out.println("Supported extensions: " + loader.getSupportedExtensions());

        try {
            Serializer javaSerializer = loader.getExtension("java");
            System.out.println("Loaded 'java': " + javaSerializer.getClass().getName());

            Serializer kryoSerializer = loader.getExtension("kryo");
            System.out.println("Loaded 'kryo': " + kryoSerializer.getClass().getName());

            Serializer protoSerializer = loader.getExtension("protobuf");
            System.out.println("Loaded 'protobuf': " + protoSerializer.getClass().getName());

        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. Test SerializerCode (which uses ExtensionLoader internally)
        System.out.println("\n--- Testing SerializerCode ---");
        try {
            Serializer s1 = SerializerCode.getSerializerByName("java");
            System.out.println("getSerializerByName('java') -> " + s1.getClass().getName() + ", Code: " + s1.getCode());

            Serializer s2 = SerializerCode.getSerializerByCode((byte) 0x02); // Kryo
            System.out.println("getSerializerByCode(0x02) -> " + s2.getClass().getName() + ", Code: " + s2.getCode());

            Serializer s3 = SerializerCode.getSerializerByName("protobuf");
            System.out.println(
                    "getSerializerByName('protobuf') -> " + s3.getClass().getName() + ", Code: " + s3.getCode());

            // Test Case Insensitivity
            Serializer s4 = SerializerCode.getSerializerByName("PROTOBUF");
            System.out.println(
                    "getSerializerByName('PROTOBUF') -> " + s4.getClass().getName() + ", Code: " + s4.getCode());

        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("\nSPI Test Finished.");
    }
}
