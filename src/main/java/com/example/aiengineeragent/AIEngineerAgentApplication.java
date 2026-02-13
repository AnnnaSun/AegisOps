package com.example.aiengineeragent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.example.aiengineeragent.config")
public class AIEngineerAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AIEngineerAgentApplication.class, args);
    }
}
