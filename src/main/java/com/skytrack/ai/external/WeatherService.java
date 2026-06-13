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
        String url = baseRequest()
                .queryParam("q", city)
                .encode()
                .build()
                .toUriString();

        return requestWeather(url, city);
    }

    public Map<String, Object> getWeatherByCoordinates(double latitude, double longitude) {
        String url = baseRequest()
                .queryParam("lat", latitude)
                .queryParam("lon", longitude)
                .encode()
                .build()
                .toUriString();

        return requestWeather(url, latitude + ", " + longitude);
    }

    private UriComponentsBuilder baseRequest() {
        return UriComponentsBuilder.fromUriString(apiUrl)
                .queryParam("appid", apiKey)
                .queryParam("units", "metric")
                .queryParam("lang", "vi");
    }

    private Map<String, Object> requestWeather(String url, String location) {
        try {
            return new RestTemplate().getForObject(url, Map.class);
        } catch (Exception e) {
            return Map.of("error", "Không thể lấy thời tiết cho " + location);
        }
    }
}
