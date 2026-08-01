package dev.portfolio.wbmon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WbPriceMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(WbPriceMonitorApplication.class, args);
    }
}
