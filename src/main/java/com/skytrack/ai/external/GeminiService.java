package com.skytrack.ai.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrack.ai.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    @Value("${app.gemini.api-key}")
    private String apiKey;

    @Value("${app.gemini.url}")
    private String apiUrl;

    private final ObjectMapper objectMapper;
    private final RestTemplateBuilder restTemplateBuilder;

    // Hàm chính: Gửi câu hỏi và nhận câu trả lời từ Gemini
    public String askGemini(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("Question is required");
        }
        String normalizedApiKey = apiKey == null ? "" : apiKey.trim();
        if (normalizedApiKey.isBlank()) {
            throw new ExternalServiceException("Gemini API key is not configured");
        }
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new ExternalServiceException("Gemini API URL is not configured");
        }

        // Cấu trúc JSON yêu cầu bởi API của Gemini
        Map<String, Object> part = Map.of("text", userPrompt);
        Map<String, Object> content = Map.of("parts", List.of(part));
        Map<String, Object> requestBody = Map.of("contents", List.of(content));

        try {
            RestTemplate restTemplate = restTemplateBuilder
                    .connectTimeout(Duration.ofSeconds(5))
                    .readTimeout(Duration.ofSeconds(45))
                    .build();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            return callGeminiWithFallback(restTemplate, entity, normalizedApiKey);

        } catch (RestClientResponseException e) {
            String geminiMessage = extractGeminiErrorMessage(e.getResponseBodyAsString());
            log.error("Gemini request failed with HTTP {}: {}", e.getStatusCode().value(), geminiMessage);
            throw new ExternalServiceException("Gemini request failed: " + geminiMessage);
        } catch (Exception e) {
            if (e instanceof ExternalServiceException externalServiceException) {
                throw externalServiceException;
            }
            log.error("Gemini request failed unexpectedly: {}", e.getMessage());
            throw new ExternalServiceException("Gemini is unavailable right now");
        }
    }

    private String callGeminiWithFallback(RestTemplate restTemplate, HttpEntity<String> entity, String normalizedApiKey) throws Exception {
        RestClientResponseException lastGeminiError = null;

        for (String modelUrl : buildCandidateUrls()) {
            try {
                String response = restTemplate.postForObject(buildRequestUrl(modelUrl, normalizedApiKey), entity, String.class);
                return extractAnswer(response);
            } catch (RestClientResponseException e) {
                lastGeminiError = e;
                String geminiMessage = extractGeminiErrorMessage(e.getResponseBodyAsString());
                if (!shouldTryFallback(e, geminiMessage)) {
                    throw e;
                }
                log.warn("Gemini model was unavailable, retrying with fallback. HTTP {}: {}", e.getStatusCode().value(), geminiMessage);
            }
        }

        if (lastGeminiError != null) {
            throw lastGeminiError;
        }
        throw new ExternalServiceException("Gemini API URL is not configured");
    }

    private List<String> buildCandidateUrls() {
        String normalizedApiUrl = apiUrl.trim();
        Set<String> urls = new LinkedHashSet<>();
        urls.add(normalizedApiUrl);

        for (String model : List.of("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")) {
            urls.add(replaceGeminiModel(normalizedApiUrl, model));
        }

        return new ArrayList<>(urls);
    }

    private String replaceGeminiModel(String url, String replacementModel) {
        return url.replaceAll("models/[^/:?]+:generateContent", "models/" + replacementModel + ":generateContent");
    }

    private boolean shouldTryFallback(RestClientResponseException e, String geminiMessage) {
        int statusCode = e.getStatusCode().value();
        String normalizedMessage = geminiMessage.toLowerCase();
        return statusCode == 429
                || statusCode == 503
                || normalizedMessage.contains("high demand")
                || normalizedMessage.contains("overloaded")
                || normalizedMessage.contains("temporarily unavailable");
    }

    private String extractAnswer(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (textNode.isMissingNode() || textNode.asText().isBlank()) {
            throw new ExternalServiceException("Gemini returned an empty response");
        }
        return textNode.asText();
    }

    private String buildRequestUrl(String normalizedApiUrl, String normalizedApiKey) {
        if (normalizedApiUrl.contains("?key=") || normalizedApiUrl.contains("&key=")) {
            return normalizedApiUrl;
        }
        return normalizedApiUrl + (normalizedApiUrl.contains("?") ? "&" : "?") + "key=" + normalizedApiKey;
    }

    private String extractGeminiErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "Gemini returned an error without a response body";
        }
        try {
            String message = objectMapper.readTree(responseBody).path("error").path("message").asText();
            if (!message.isBlank()) {
                return message;
            }
        } catch (Exception ignored) {
            // Fall through to a short raw body below.
        }
        return responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody;
    }
}
