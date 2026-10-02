package com.orcaai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OrcaAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrcaAiApplication.class, args);
    }
}
