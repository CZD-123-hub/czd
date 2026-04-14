package com.coding.assistant;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.coding.assistant.mapper")
public class CodingAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodingAssistantApplication.class, args);
    }
}