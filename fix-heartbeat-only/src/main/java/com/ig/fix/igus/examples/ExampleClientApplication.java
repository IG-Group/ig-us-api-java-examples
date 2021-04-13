package com.ig.fix.igus.examples;

import static java.lang.Thread.currentThread;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import io.allune.quickfixj.spring.boot.starter.EnableQuickFixJClient;
import io.allune.quickfixj.spring.boot.starter.autoconfigure.QuickFixJBootProperties;
import lombok.SneakyThrows;
import quickfix.Application;
import quickfix.ConfigError;
import quickfix.Initiator;
import quickfix.LogFactory;
import quickfix.MessageCracker;
import quickfix.MessageFactory;
import quickfix.MessageStoreFactory;
import quickfix.SessionSettings;
import quickfix.ThreadedSocketInitiator;

/**
 * see
 * https://github.com/esanchezros/quickfixj-spring-boot-starter-examples/tree/master/simple-client/src/main/java/io/allune/quickfixj/spring/boot/starter/examples/client
 * 
 * @author papados
 *
 */
@EnableQuickFixJClient
@SpringBootApplication
public class ExampleClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExampleClientApplication.class, args);
    }
    
    

    @Bean
    public Application clientApplication() {
        return new FixApplication(messageCracker());
    }

    @Bean
    public MessageCracker messageCracker() {
        return new FixMessageCracker();
    }
    /**
     * substituting bean from io.allune.quickfixj.spring.boot.starter.autoconfigure.client.QuickFixJClientAutoConfiguration.clientSessionSettings(QuickFixJBootProperties) 
     * @param properties
     * @return
     */
    @Bean(name = "clientSessionSettings")
    @SneakyThrows
    public SessionSettings clientSessionSettings(@Value("${app.session.comp-id}") String compId,//
            @Value("${app.session.host}") String host,//
            @Value("${app.session.port}") int port,//
            @Value("${app.session.username}") String username,//
            @Value("${app.session.password}") String password,
            QuickFixJBootProperties properties) {
        ClassLoader classLoader = currentThread().getContextClassLoader();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);

        String location = properties.getClient().getConfig();
        Resource resource = resolver.getResource(location);
        
        Properties props = new Properties();
        props.put("comp-id", compId);
        props.put("host", host);
        props.put("port", ""+port);
        props.put("username", username);
        props.put("password", password);

        return new SessionSettings(resource.getInputStream(), props);
    }
    @Bean
    public Initiator clientInitiator(quickfix.Application clientApplication,
            MessageStoreFactory clientMessageStoreFactory, SessionSettings clientSessionSettings,
            LogFactory clientLogFactory, MessageFactory clientMessageFactory) throws ConfigError {
        return new ThreadedSocketInitiator(clientApplication, clientMessageStoreFactory, clientSessionSettings,
                clientLogFactory, clientMessageFactory);
    }


}
