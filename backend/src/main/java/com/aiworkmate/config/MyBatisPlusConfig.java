package com.aiworkmate.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.aiworkmate.mapper")
public class MyBatisPlusConfig {
}
