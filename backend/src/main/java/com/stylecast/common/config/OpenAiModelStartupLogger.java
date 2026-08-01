package com.stylecast.common.config;

import com.stylecast.occasion.OccasionClassifierProperties;
import com.stylecast.retail.RetailSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Logs which OpenAI model each OpenAI-backed feature is configured to use,
 * once at application startup - so the effective, configured model (never
 * an SDK/library default) is always visible in the logs regardless of
 * environment.
 *
 * <p>Deliberately logs ONLY the model identifier for each feature - never
 * the API key, an {@code Authorization} header, or any prompt content (see
 * {@link RetailSearchProperties}/{@link OccasionClassifierProperties},
 * neither of which is logged in full here).
 */
@Component
public class OpenAiModelStartupLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OpenAiModelStartupLogger.class);

    private final RetailSearchProperties retailSearchProperties;
    private final OccasionClassifierProperties occasionClassifierProperties;

    public OpenAiModelStartupLogger(
            RetailSearchProperties retailSearchProperties,
            OccasionClassifierProperties occasionClassifierProperties) {
        this.retailSearchProperties = retailSearchProperties;
        this.occasionClassifierProperties = occasionClassifierProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("OpenAI retail search model: {}", retailSearchProperties.openaiModel());
        log.info("Occasion classifier model: {}", occasionClassifierProperties.openaiModel());
    }
}
