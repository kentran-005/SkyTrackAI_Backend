package com.skytrack.ai.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrack.ai.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    @Value("${app.gemini.api-key}")
    private String apiKey;

    @Value("${app.gemini.url}")
    private String apiUrl;

    private final ObjectMapper objectMapper; // Dùng để parse JSON

    // Hàm chính: Gửi câu hỏi và nhận câu trả lời từ Gemini
    public String askGemini(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("Question is required");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new ExternalServiceException("Gemini API key is not configured");
        }

        String requestUrl = apiUrl + "?key=" + apiKey;

        // Cấu trúc JSON yêu cầu bởi API của Gemini
        Map<String, Object> part = Map.of("text", userPrompt);
        Map<String, Object> content = Map.of("parts", List.of(part));
        Map<String, Object> requestBody = Map.of("contents", List.of(content));

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            // Gọi API
            String response = restTemplate.postForObject(requestUrl, entity, String.class);

            // Parse JSON để lấy câu trả lời
            JsonNode root = objectMapper.readTree(response);
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                throw new ExternalServiceException("Gemini returned an empty response");
            }
            return textNode.asText();

        } catch (Exception e) {
            if (e instanceof ExternalServiceException externalServiceException) {
                throw externalServiceException;
            }
            log.error("Gemini request failed: {}", e.getMessage());
            throw new ExternalServiceException("Gemini is unavailable or rejected the configured API key");
        }
    }
}
