
package com.ig.fix.igus.examples;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class ExampleClientApplication {

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(ExampleClientApplication.class, args);
        TimeUnit.SECONDS.sleep(Integer.MAX_VALUE); //stay up
    }
}
