package com.sgtransport.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class SgTransportApplication {

	public static void main(String[] args) {
		SpringApplication.run(SgTransportApplication.class, args);
	}

	@Bean
	public WebMvcConfigurer corsConfigurer() {
		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				registry.addMapping("/api/**")
					.allowedOrigins(
						"http://localhost:4200",
						"http://127.0.0.1:4200",
						"http://localhost:3000",
						"http://127.0.0.1:3000"
					)
					.allowedMethods("GET", "POST", "PUT", "DELETE")
					.allowCredentials(true);
			}
		};
	}
}
