package com.back.mozu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MozuApplication {

    public static void main(String[] args) {
        SpringApplication.run(MozuApplication.class, args);
    }

}
