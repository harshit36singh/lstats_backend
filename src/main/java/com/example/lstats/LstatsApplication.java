package com.example.lstats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class LstatsApplication {

	public static void main(String[] args) {
		SpringApplication.run(LstatsApplication.class, args);
	}

}
