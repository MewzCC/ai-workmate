package com.aiworkmate.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "com.aiworkmate", annotationClass = Mapper.class)
public class MyBatisPlusConfig {
}
