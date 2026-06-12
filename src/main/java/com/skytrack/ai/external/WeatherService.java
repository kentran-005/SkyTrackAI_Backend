package com.skytrack.ai.external;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class WeatherService {

    @Value("${app.openweather.api-key}")
    private String apiKey;

    @Value("${app.openweather.url}")
    private String apiUrl;

    // Lấy thời tiết theo tên thành phố (VD: "Ho Chi Minh", "Ha Noi")
    public Map<String, Object> getWeatherByCity(String city) {
        String url = UriComponentsBuilder.fromUriString(apiUrl)
                .queryParam("q", city)
                .queryParam("appid", apiKey)
                .queryParam("units", "metric")
                .queryParam("lang", "vi")
                .encode()
                .build()
                .toUriString();

        RestTemplate restTemplate = new RestTemplate();
        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response;
        } catch (Exception e) {
            return Map.of("error", "Không thể lấy thời tiết cho " + city);
        }
    }
}
