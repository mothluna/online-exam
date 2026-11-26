package com.example.oes;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.example.oes.mapper")
public class OesApplication {

    public static void main(String[] args) {
        SpringApplication.run(OesApplication.class, args);
    }
}
