package com.cargotrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CargotrackApplication {

	public static void main(String[] args) {
		SpringApplication.run(CargotrackApplication.class, args);
	}

}
