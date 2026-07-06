package com.company.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReportCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReportCoreApplication.class, args);
    }
}
