package com.gimnasio.sistemagym;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SistemaGymApplication {

	public static void main(String[] args) {
		SpringApplication.run(SistemaGymApplication.class, args);
	}

}
