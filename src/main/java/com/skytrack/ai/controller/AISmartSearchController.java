package com.skytrack.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrack.ai.external.GeminiService;
import com.skytrack.ai.repository.FlightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AISmartSearchController {

    private final GeminiService geminiService;
    private final FlightRepository flightRepository;
    private final ObjectMapper objectMapper;

    @PostMapping("/smart-search")
    public ResponseEntity<?> smartSearch(@RequestBody Map<String, String> body) {
        String query = body.get("query");
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Query is required");
        }

        // 1. Tạo Prompt yêu cầu Gemini extract JSON
        String prompt = "Analyze this flight search query and extract origin airport code, destination airport code, and date (YYYY-MM-DD). " +
                "Return ONLY a valid JSON like {\"departure\": \"SGN\", \"arrival\": \"HAN\", \"date\": \"2024-06-01\"}. Query: " + query;

        try {
            // 2. Gọi Gemini
            String aiResponse = geminiService.askGemini(prompt);

            // 3. Parse JSON từ Gemini
            aiResponse = aiResponse.replaceAll("```json", "").replaceAll("```", "").trim();

            String dep = extractCode(aiResponse, "departure");
            String arr = extractCode(aiResponse, "arrival");

            // 4. Tìm trong DB
            if (dep != null && arr != null) {
                var flights = flightRepository.findAll().stream()
                        .filter(f -> f.getDepartureAirport() != null
                                && f.getArrivalAirport() != null
                                && f.getDepartureAirport().getCode().equalsIgnoreCase(dep)
                                && f.getArrivalAirport().getCode().equalsIgnoreCase(arr))
                        .toList();
                return ResponseEntity.ok(Map.of("flights", flights, "aiMessage", "Found " + flights.size() + " flights from " + dep + " to " + arr));
            } else {
                return ResponseEntity.ok(Map.of("flights", flightRepository.findAll(), "aiMessage", "Showing all available flights"));
            }

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("AI Search failed: " + e.getMessage());
        }
    }

    private String extractCode(String json, String key) {
        try {
            JsonNode node = objectMapper.readTree(json).path(key);
            if (node.isMissingNode() || node.isNull() || node.asText().isBlank()) {
                return null;
            }
            return node.asText().trim().toUpperCase();
        } catch (Exception e) {
            return null;
        }
    }
}
