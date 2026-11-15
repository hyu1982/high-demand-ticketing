package demo.highdemand;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class HighDemandApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(HighDemandApiGatewayApplication.class, args);
    }
}