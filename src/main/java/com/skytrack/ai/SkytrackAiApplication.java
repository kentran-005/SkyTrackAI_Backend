package com.skytrack.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
		org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class // THÊM DÒNG NÀY
})
@EnableScheduling
@ConfigurationPropertiesScan
public class SkytrackAiApplication {
	public static void main(String[] args) {
		SpringApplication.run(SkytrackAiApplication.class, args);
	}
}