package com.example.otaconsole;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OtaConsoleApplication {
    public static void main(String[] args) {
        SpringApplication.run(OtaConsoleApplication.class, args);
    }
}
