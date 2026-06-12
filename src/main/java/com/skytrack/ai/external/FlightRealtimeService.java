package com.skytrack.ai.external;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class FlightRealtimeService {

    @Value("${app.aviationstack.api-key:}")
    private String apiKey;

    @Value("${app.aviationstack.url:}")
    private String apiUrl;

    // Lấy dữ liệu chuyến bay trực tiếp (Real-time flights)
    public Map<String, Object> getLiveFlights() {
        // Gọi API lấy các chuyến bay đang bay hiện tại
        String url = UriComponentsBuilder.fromUriString(apiUrl + "/flights")
                .queryParam("access_key", apiKey)
                .queryParam("limit", 50)
                .encode()
                .build()
                .toUriString();
        RestTemplate restTemplate = new RestTemplate();
        try {
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            return Map.of("error", "Không thể lấy dữ liệu chuyến bay thực tế: " + e.getMessage());
        }
    }

    // Tìm kiếm 1 chuyến bay cụ thể (Dùng cho AI Assistant hoặc Search)
    public Map<String, Object> searchFlight(String flightCode) {
        // Ví dụ tìm chuyến VN220
        String url = UriComponentsBuilder.fromUriString(apiUrl + "/flights")
                .queryParam("access_key", apiKey)
                .queryParam("flight_iata", flightCode)
                .encode()
                .build()
                .toUriString();
        RestTemplate restTemplate = new RestTemplate();
        try {
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            return Map.of("error", "Không tìm thấy chuyến bay " + flightCode);
        }
    }
}
