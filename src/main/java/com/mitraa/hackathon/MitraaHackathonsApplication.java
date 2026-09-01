package com.mitraa.hackathon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MitraaHackathonsApplication {
    public static void main(String[] args) {
        SpringApplication.run(MitraaHackathonsApplication.class, args);
    }
}
