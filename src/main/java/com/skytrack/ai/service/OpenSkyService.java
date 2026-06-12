package com.skytrack.ai.service;

import com.skytrack.ai.config.OpenSkyConfig;
import com.skytrack.ai.dto.FlightStateDto;
import com.skytrack.ai.entity.RealtimeFlight;
import com.skytrack.ai.repository.RealtimeFlightRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenSkyService {

    private final OpenSkyConfig config;
    private final RealtimeFlightRepository realtimeFlightRepository; // Inject Repository mới
    private final RestClient.Builder restClientBuilder;

    private RestClient restClient;

    @PostConstruct
    public void init() {
        RestClient.Builder builder = restClientBuilder.baseUrl(config.getBaseUrl());

        if (hasOpenSkyCredentials()) {
            builder.defaultHeaders(headers -> headers.setBasicAuth(config.getUsername(), config.getPassword()));
        } else {
            log.warn("OpenSky credentials are not configured. Realtime flights will use public anonymous access.");
        }

        this.restClient = builder.build();

        log.info("OpenSky RestClient initialized with base URL {}", config.getBaseUrl());
    }

    /**
     * Lấy dữ liệu chuyến bay (Ưu tiên DB, DB cũ quá thì gọi API)
     */
    @Transactional
    public List<FlightStateDto> getFlights() {
        if (this.restClient == null) return Collections.emptyList();
        // 1. Kiểm tra xem trong DB có dữ liệu không
        List<RealtimeFlight> currentFlights = realtimeFlightRepository.findAll();

        if (!currentFlights.isEmpty()) {
            // 2. Kiểm tra xem dữ liệu đã cũ chưa (lấy mốc thời gian của bản ghi đầu tiên)
            LocalDateTime lastUpdateTime = currentFlights.get(0).getLastUpdated();
            long secondsOld = lastUpdateTime == null
                    ? Long.MAX_VALUE
                    : ChronoUnit.SECONDS.between(lastUpdateTime, LocalDateTime.now());

            // Nếu dữ liệu còn mới (< 60s), trả về DB ngay lập tức (KHÔNG GỌI API)
            if (secondsOld < config.getCacheRefreshSeconds()) {
                log.debug("Trả dữ liệu từ DB (Cập nhật cách đây {} giây)", secondsOld);
                return mapEntitiesToDtos(currentFlights);
            }
        }

        // 3. Nếu DB trống HOẶC dữ liệu đã quá cũ -> Gọi API OpenSky
        log.info("Dữ liệu DB trống hoặc quá cũ. Đang gọi API OpenSky...");
        fetchAndSaveToDatabase();

        // 4. Trả về dữ liệu mới vừa được cập nhật vào DB
        return mapEntitiesToDtos(realtimeFlightRepository.findAll());
    }

    /**
     * Gọi API và Lưu vào Database
     */
    private synchronized void fetchAndSaveToDatabase() {
        if (this.restClient == null) return;
        try {
            String url = "/states/all?lamin=8&lamax=24&lomin=100&lomax=112";

            // OpenSky trả về JSON dạng: {"time": 12345, "states": [[...], [...]]}
            // Nên ta dùng Map để bắt dữ liệu
            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null && response.containsKey("states")) {
                // Lấy mảng states ra
                List<List<Object>> rawStates = (List<List<Object>>) response.get("states");
                LocalDateTime now = LocalDateTime.now();

                if (rawStates != null && !rawStates.isEmpty()) {
                    List<RealtimeFlight> flightEntities = rawStates.stream()
                            .map(state -> mapToEntity(state, now))
                            .collect(Collectors.toList());

                    // XÓA TOÀN BỘ DỮ LIỆU CŨ trong DB
                    realtimeFlightRepository.deleteAll();

                    // LƯU DỮ LIỆU MỚI VÀO DB
                    realtimeFlightRepository.saveAll(flightEntities);

                    realtimeFlightRepository.flush();

                    log.info("✅ Đã lưu {} chuyến bay từ OpenSky vào Database", flightEntities.size());
                } else {
                    log.warn("⚠️ OpenSky trả về dữ liệu rỗng (Có thể khu vực này không có chuyến bay nào).");
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch OpenSky flights", e);
        }
    }

    // --- HÀM HELPER MAPPER ---

    private RealtimeFlight mapToEntity(List<Object> state, LocalDateTime now) {
        RealtimeFlight flight = new RealtimeFlight();
        flight.setIcao24(getStringValue(state, 0));
        flight.setCallsign(getStringValue(state, 1).trim());
        flight.setOriginCountry(getStringValue(state, 2));
        flight.setLongitude(getDoubleValue(state, 5));
        flight.setLatitude(getDoubleValue(state, 6));
        flight.setAltitude(getDoubleValue(state, 7));
        flight.setOnGround(getBooleanValue(state, 8));
        flight.setVelocity(getDoubleValue(state, 9));
        flight.setHeading(getDoubleValue(state, 10));
        flight.setLastUpdated(now);
        return flight;
    }

    private List<FlightStateDto> mapEntitiesToDtos(List<RealtimeFlight> flights) {
        return flights.stream().map(f -> {
            FlightStateDto dto = new FlightStateDto();
            dto.setIcao24(f.getIcao24());
            dto.setCallsign(f.getCallsign());
            dto.setOriginCountry(f.getOriginCountry());
            dto.setLongitude(f.getLongitude());
            dto.setLatitude(f.getLatitude());
            dto.setAltitude(f.getAltitude());
            dto.setVelocity(f.getVelocity());
            dto.setHeading(f.getHeading());
            dto.setOnGround(f.getOnGround());
            return dto;
        }).collect(Collectors.toList());
    }

    private String getStringValue(List<Object> list, int index) {
        return index < list.size() && list.get(index) != null ? list.get(index).toString() : "";
    }
    private Double getDoubleValue(List<Object> list, int index) {
        return index < list.size() && list.get(index) != null ? Double.parseDouble(list.get(index).toString()) : null;
    }
    private Boolean getBooleanValue(List<Object> list, int index) {
        return index < list.size() && list.get(index) != null && Boolean.parseBoolean(list.get(index).toString());
    }

    private boolean hasOpenSkyCredentials() {
        return config.getUsername() != null && !config.getUsername().isBlank()
                && config.getPassword() != null && !config.getPassword().isBlank();
    }
}
