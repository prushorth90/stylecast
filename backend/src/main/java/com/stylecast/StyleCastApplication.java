package com.stylecast;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StyleCastApplication {

    public static void main(String[] args) {
        SpringApplication.run(StyleCastApplication.class, args);
    }
}
