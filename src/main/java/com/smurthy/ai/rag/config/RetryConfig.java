package com.smurthy.ai.rag.config;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Map;

@Configuration
public class RetryConfig {

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(2000); // Initial wait time: 2 seconds
        backOffPolicy.setMultiplier(3);         // Multiply wait time by 3 on each retry
        backOffPolicy.setMaxInterval(15000);    // Max wait time: 15 seconds
        retryTemplate.setBackOffPolicy(backOffPolicy);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(4, Map.of(NonTransientAiException.class, true));
        retryTemplate.setRetryPolicy(retryPolicy);

        return retryTemplate;
    }
}