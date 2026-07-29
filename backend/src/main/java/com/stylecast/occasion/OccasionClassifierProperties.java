package com.stylecast.occasion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the OpenAI-backed occasion classifier, bound from
 * {@code stylecast.occasion-classifier.*} (see application.yml). Every value
 * has a default so the application starts fine without any of these
 * environment variables set; {@link #hasApiKey()} being {@code false} is a
 * normal, supported state that simply means every classification uses
 * {@link RuleBasedOccasionClassifier} instead.
 *
 * <p>Reuses the same {@code OPENAI_API_KEY} / {@code OPENAI_API_BASE_URL}
 * environment variables as {@code com.stylecast.retail}'s retail-search
 * configuration, since both call the same OpenAI account.
 *
 * @param openaiApiKey     OpenAI API key (env var {@code OPENAI_API_KEY}); blank if not configured
 * @param openaiModel      model id to use for the structured-output classification call
 * @param baseUrl          base URL of the OpenAI Responses API; overridable in tests to
 *                         point at a local fake HTTP server instead of the real API
 * @param connectTimeoutMs HTTP connect timeout in milliseconds
 * @param readTimeoutMs    HTTP response timeout in milliseconds
 */
@ConfigurationProperties(prefix = "stylecast.occasion-classifier")
public record OccasionClassifierProperties(
        String openaiApiKey,
        String openaiModel,
        String baseUrl,
        long connectTimeoutMs,
        long readTimeoutMs
) {
    public boolean hasApiKey() {
        return openaiApiKey != null && !openaiApiKey.isBlank();
    }
}
