package com.sprotshop.sportstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
public class SportstoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(SportstoreApplication.class, args);
	}

}
