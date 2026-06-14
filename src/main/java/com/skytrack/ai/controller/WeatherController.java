package com.skytrack.ai.controller;

import com.skytrack.ai.external.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping("/{city}")
    public ResponseEntity<Map<String, Object>> getWeather(@PathVariable String city) {
        return weatherResponse(weatherService.getWeatherByCity(city));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getWeatherByCoordinates(
            @RequestParam double latitude,
            @RequestParam double longitude
    ) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tọa độ sân bay không hợp lệ"));
        }
        return weatherResponse(weatherService.getWeatherByCoordinates(latitude, longitude));
    }

    private ResponseEntity<Map<String, Object>> weatherResponse(Map<String, Object> weather) {
        if (weather.containsKey("error")) {
            return ResponseEntity.status(502).body(weather);
        }
        return ResponseEntity.ok(weather);
    }
}
