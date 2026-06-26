package com.demo.dfbatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.task.configuration.EnableTask;

@SpringBootApplication
@EnableTask
public class DfbatchApplication {

	public static void main(String[] args) {
		SpringApplication.run(DfbatchApplication.class, args);
	}

}
