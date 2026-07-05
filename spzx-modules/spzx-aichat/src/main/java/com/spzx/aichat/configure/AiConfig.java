package com.spzx.aichat.configure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AiConfig {

    @Value("${ai.base-url}")
    private String baseUrl;

    //WebClient 是 Spring WebFlux 提供的非阻塞、响应式 HTTP 客户端。
    @Bean
    public WebClient aiWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}