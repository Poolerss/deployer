package com.deployer;

import com.deployer.config.DeployerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DeployerProperties.class)
public class DeployerApplication {

	public static void main(String[] args) {
		SpringApplication.run(DeployerApplication.class, args);
	}
}
