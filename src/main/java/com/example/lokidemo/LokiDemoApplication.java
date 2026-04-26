package com.example.lokidemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LokiDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(LokiDemoApplication.class, args);
    }
}
