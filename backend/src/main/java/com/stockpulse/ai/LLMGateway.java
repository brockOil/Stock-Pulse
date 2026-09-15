package com.stockpulse.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provider-specific HTTP for Gemini, Groq, and Ollama, all normalized behind callLLM /
 * streamLLM. Prompt construction lives in PromptBuilder; parsing, validation, and fallback
 * live in the AI strategy classes - this component's only job is "send a prompt, get text back".
 *
 * Configure via application.properties (llm.provider, llm.api-key, llm.model, llm.base-url).
 * The API key is read from an environment variable (LLM_API_KEY) and is never logged or
 * echoed back to callers.
 */
@Component
public class LLMGateway {

    private static final Logger log = LoggerFactory.getLogger(LLMGateway.class);

    private final String provider;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final RestClient http;
    private final ObjectMapper objectMapper;

    public LLMGateway(
            @Value("${llm.provider}") String provider,
            @Value("${llm.api-key:}") String apiKey,
            @Value("${llm.model}") String model,
            @Value("${llm.base-url}") String baseUrl,
            @Value("${llm.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${llm.read-timeout-ms}") int readTimeoutMs,
            ObjectMapper objectMapper) {
        this.provider = provider;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** Blocking call returning the raw text response. Never throws a provider-specific exception - always AiUnavailableException. */
    public String callLLM(String prompt) {
        try {
            return switch (provider.toLowerCase(Locale.ROOT)) {
                case "gemini" -> callGemini(prompt);
                case "groq" -> callOpenAiCompatible(prompt, baseUrl + "/openai/v1/chat/completions", true);
                case "ollama" -> callOpenAiCompatible(prompt, baseUrl + "/v1/chat/completions", !apiKey.isBlank());
                default -> throw new AiUnavailableException("Unknown LLM provider configured: " + provider);
            };
        } catch (AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new AiUnavailableException("LLM call failed: " + e.getClass().getSimpleName(), e);
        }
    }

    /**
     * Streams tokens as they arrive (bonus SSE endpoint). Only implemented for the
     * OpenAI-compatible providers (Groq, Ollama), which is the common local-dev path. Gemini
     * requests fall back to a single non-streaming call whose text is emitted as one chunk.
     */
    public void streamLLM(String prompt, Consumer<String> onToken) {
        try {
            if ("gemini".equalsIgnoreCase(provider)) {
                onToken.accept(callGemini(prompt));
                return;
            }
            String url = "groq".equalsIgnoreCase(provider)
                    ? baseUrl + "/openai/v1/chat/completions"
                    : baseUrl + "/v1/chat/completions";
            boolean withAuth = "groq".equalsIgnoreCase(provider) || !apiKey.isBlank();
            streamOpenAiCompatible(prompt, url, withAuth, onToken);
        } catch (AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new AiUnavailableException("LLM stream failed: " + e.getClass().getSimpleName(), e);
        }
    }

    private String callGemini(String prompt) {
        String path = baseUrl + "/v1beta/models/" + model + ":generateContent";
        URI uri = UriComponentsBuilder.fromHttpUrl(path)
                .queryParam("key", apiKey)
                .build()
                .encode()
                .toUri();
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature", 0.3)
        );
        JsonNode response = http.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new AiUnavailableException("Empty response from Gemini");
        }
        JsonNode textNode = response.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (textNode.isMissingNode()) {
            throw new AiUnavailableException("Unexpected Gemini response shape");
        }
        return textNode.asText();
    }

    private String callOpenAiCompatible(String prompt, String url, boolean withAuth) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.3
        );
        RestClient.RequestBodySpec spec = http.post().uri(url).contentType(MediaType.APPLICATION_JSON);
        if (withAuth) {
            spec = spec.header("Authorization", "Bearer " + apiKey);
        }
        JsonNode response = spec.body(body).retrieve().body(JsonNode.class);

        if (response == null) {
            throw new AiUnavailableException("Empty response from LLM provider");
        }
        JsonNode contentNode = response.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode()) {
            throw new AiUnavailableException("Unexpected provider response shape");
        }
        return contentNode.asText();
    }

    private void streamOpenAiCompatible(String prompt, String url, boolean withAuth, Consumer<String> onToken) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.putArray("messages").addObject().put("role", "user").put("content", prompt);
        body.put("temperature", 0.3);
        body.put("stream", true);

        RestClient.RequestBodySpec spec = http.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM);
        if (withAuth) {
            spec = spec.header("Authorization", "Bearer " + apiKey);
        }

        spec.body(body).exchange((request, response) -> {
            try (InputStream is = response.getBody();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) {
                        continue;
                    }
                    JsonNode chunk = objectMapper.readTree(data);
                    JsonNode delta = chunk.path("choices").path(0).path("delta").path("content");
                    if (delta.isTextual() && !delta.asText().isEmpty()) {
                        onToken.accept(delta.asText());
                    }
                }
            }
            return null;
        });
    }
}
