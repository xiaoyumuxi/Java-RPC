package com.xiaoyu.rpc.core.serialization;

import com.google.gson.*;
import com.xiaoyu.rpc.common.serialization.Serializer;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;

@Slf4j
public class JsonSerializerImpl implements Serializer {

    private final Gson gson;

    public JsonSerializerImpl() {
        this.gson = new GsonBuilder()
                .registerTypeHierarchyAdapter(com.google.protobuf.ByteString.class,
                        new TypeAdapter<com.google.protobuf.ByteString>() {
                            @Override
                            public void write(com.google.gson.stream.JsonWriter out,
                                    com.google.protobuf.ByteString value) throws java.io.IOException {
                                if (value == null) {
                                    out.nullValue();
                                    return;
                                }
                                out.value(java.util.Base64.getEncoder().encodeToString(value.toByteArray()));
                            }

                            @Override
                            public com.google.protobuf.ByteString read(com.google.gson.stream.JsonReader in)
                                    throws java.io.IOException {
                                String s = in.nextString();
                                return com.google.protobuf.ByteString.copyFrom(java.util.Base64.getDecoder().decode(s));
                            }
                        })
                .setDateFormat("yyyy-MM-dd HH:mm:ss")
                .create();
    }

    @Override
    public byte getCode() {
        return 0x04;
    }

    @Override
    public byte[] serialize(Object object) {
        if (object == null) {
            return new byte[0];
        }
        String json = gson.toJson(object);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String json = new String(bytes, StandardCharsets.UTF_8);
        return gson.fromJson(json, clazz);
    }
}
