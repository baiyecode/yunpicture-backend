package com.baiye.yupicturebackend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Spring MVC Json 配置
 */
@JsonComponent
public class JsonConfig {


    /**
     * 为什么这样做？ JavaScript 中的 Number 类型有精度限制，
     * 无法安全地表示所有 64 位的 Java Long 值（超过 Number.MAX_SAFE_INTEGER 或低于 Number.MIN_SAFE_INTEGER 的值）。
     * 通过将 Long 序列化为字符串，可以确保客户端 JavaScript 能够安全地接收和处理这些 ID，避免精度丢失。
     */

    /**
     * 添加 Long 转 json 精度丢失的配置
     */
    @Bean
    public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
        //调用 builder 的方法，明确指定不创建用于 XML 处理的 XmlMapper。虽然 false 通常是默认值，但显式指定可以增加清晰度。
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        SimpleModule module = new SimpleModule();
        // Long -> String
        module.addSerializer(Long.class, ToStringSerializer.instance);
        // long -> String
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        objectMapper.registerModule(module);
        return objectMapper;
    }
}
