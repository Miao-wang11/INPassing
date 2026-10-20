package com.ikeu.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = "com.ikeu")
@EnableTransactionManagement
@EnableCaching
@EnableScheduling
@Slf4j
public class RunningerrandsServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RunningerrandsServerApplication.class, args);
        log.info("Server Started");
    }
}
