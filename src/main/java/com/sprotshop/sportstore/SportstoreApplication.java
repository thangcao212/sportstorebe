package com.sprotshop.sportstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableCaching
public class SportstoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(SportstoreApplication.class, args);
	}

}
