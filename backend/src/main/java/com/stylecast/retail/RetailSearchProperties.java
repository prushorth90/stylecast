package com.stylecast.retail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the OpenAI-backed retail product search provider, bound
 * from {@code stylecast.retail-search.*} (see application.yml). Every value
 * has a default so the application starts without any of these environment
 * variables set; {@link #openaiApiKey()} being blank is a normal, supported
 * state that only prevents the live search call itself from succeeding.
 *
 * @param openaiApiKey             OpenAI API key (env var {@code OPENAI_API_KEY}); blank if not configured
 * @param openaiModel              model id to use for the Responses API {@code web_search} call
 * @param baseUrl                  base URL of the OpenAI Responses API; overridable in tests to
 *                                 point at a local fake HTTP server instead of the real API
 * @param connectTimeoutMs         HTTP connect timeout in milliseconds (also used by {@link OpenAiProductDetailEnricher})
 * @param readTimeoutMs            HTTP response timeout in milliseconds (also used by {@link OpenAiProductDetailEnricher})
 * @param maxResultLimit           upper bound allowed for a request's {@code limit} field
 * @param enrichmentMaxCandidates  upper bound on how many candidates per search call
 *                                 {@link OpenAiNordstromProductSearchProvider} will attempt to enrich with
 *                                 {@link OpenAiProductDetailEnricher} - bounds the extra API calls/latency
 *                                 a single search can add, regardless of the request's own {@code limit}
 */
@ConfigurationProperties(prefix = "stylecast.retail-search")
public record RetailSearchProperties(
        String openaiApiKey,
        String openaiModel,
        String baseUrl,
        long connectTimeoutMs,
        long readTimeoutMs,
        int maxResultLimit,
        int enrichmentMaxCandidates
) {
    public boolean hasApiKey() {
        return openaiApiKey != null && !openaiApiKey.isBlank();
    }
}
