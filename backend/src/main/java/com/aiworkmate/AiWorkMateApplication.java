package com.aiworkmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiWorkMateApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiWorkMateApplication.class, args);
    }
}
