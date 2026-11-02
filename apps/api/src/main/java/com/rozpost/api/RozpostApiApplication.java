package com.rozpost.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RozpostApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(RozpostApiApplication.class, args);
    }
}
