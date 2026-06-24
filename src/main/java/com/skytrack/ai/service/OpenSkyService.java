package com.skytrack.ai.service;

import com.skytrack.ai.config.OpenSkyConfig;
import com.skytrack.ai.dto.FlightStateDto;
import com.skytrack.ai.dto.RealtimeFlightStatusDto;
import com.skytrack.ai.entity.RealtimeFlight;
import com.skytrack.ai.repository.RealtimeFlightRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
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
    private volatile LocalDateTime nextRefreshAllowedAt = LocalDateTime.MIN;
    private volatile String lastFetchMessage = "Waiting for the first OpenSky refresh";
    private volatile String lastAuthenticationMessage = "OpenSky OAuth credentials have not been checked yet.";
    private volatile boolean lastRequestUsedOAuth = false;
    private volatile String accessToken;
    private volatile LocalDateTime accessTokenExpiresAt = LocalDateTime.MIN;

    @PostConstruct
    public void init() {
        this.restClient = restClientBuilder
                .baseUrl(config.getBaseUrl())
                .requestFactory(openSkyRequestFactory())
                .build();

        if (hasOAuthCredentials()) {
            log.info("OpenSky RestClient initialized with OAuth2 authentication");
        } else {
            log.warn("OpenSky OAuth credentials are not configured. Realtime flights will use rate-limited anonymous access.");
        }
    }

    /**
     * Lấy dữ liệu chuyến bay (Ưu tiên DB, DB cũ quá thì gọi API)
     */
    @Transactional(readOnly = true)
    public List<FlightStateDto> getFlights() {
        return mapEntitiesToDtos(realtimeFlightRepository.findAll());
    }

    @Scheduled(
            fixedDelayString = "${app.opensky.scheduler-delay-ms:15000}",
            initialDelayString = "${app.opensky.scheduler-delay-ms:15000}"
    )
    @Transactional
    public void refreshFlightsInBackground() {
        if (!config.isSchedulerEnabled() || this.restClient == null) return;
        if (LocalDateTime.now().isBefore(nextRefreshAllowedAt)) return;

        List<RealtimeFlight> currentFlights = realtimeFlightRepository.findAll();
        LocalDateTime lastUpdate = currentFlights.stream()
                .map(RealtimeFlight::getLastUpdated)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        long secondsOld = lastUpdate == null
                ? Long.MAX_VALUE
                : ChronoUnit.SECONDS.between(lastUpdate, LocalDateTime.now());
        if (!currentFlights.isEmpty() && secondsOld < refreshIntervalSeconds()) return;

        log.info("Realtime cache is stale. Refreshing OpenSky in the background...");
        fetchAndSaveToDatabase();
    }

    /**
     * Gọi API và Lưu vào Database
     */
    private synchronized void fetchAndSaveToDatabase() {
        if (this.restClient == null) return;
        if (LocalDateTime.now().isBefore(nextRefreshAllowedAt)) return;

        try {
            String url = "/states/all?lamin=8&lamax=24&lomin=100&lomax=112";

            // OpenSky trả về JSON dạng: {"time": 12345, "states": [[...], [...]]}
            // Nên ta dùng Map để bắt dữ liệu
            RestClient.RequestHeadersSpec<?> request = restClient.get().uri(url);
            String token = getAccessToken();
            if (token != null) {
                request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            boolean requestUsedOAuth = token != null;
            Map<String, Object> response = request.retrieve()
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

                    nextRefreshAllowedAt = now.plusSeconds(refreshIntervalSeconds());
                    lastRequestUsedOAuth = requestUsedOAuth;
                    lastFetchMessage = "Live OpenSky data via " + authenticationMode();
                    log.info("✅ Đã lưu {} chuyến bay từ OpenSky vào Database", flightEntities.size());
                } else {
                    nextRefreshAllowedAt = now.plusSeconds(refreshIntervalSeconds());
                    lastFetchMessage = "OpenSky returned no aircraft for the configured area";
                    log.warn("⚠️ OpenSky trả về dữ liệu rỗng (Có thể khu vực này không có chuyến bay nào).");
                }
            } else {
                nextRefreshAllowedAt = LocalDateTime.now().plusSeconds(refreshIntervalSeconds());
                lastFetchMessage = "OpenSky returned an invalid response. Showing cached traffic.";
                log.warn("OpenSky response did not contain a states field.");
            }
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 429) {
                long retrySeconds = parseRetrySeconds(e);
                nextRefreshAllowedAt = LocalDateTime.now().plusSeconds(retrySeconds);
                lastFetchMessage = "OpenSky rate limit reached. Showing cached traffic.";
                log.warn("OpenSky rate limited this server. Next refresh after {} seconds.", retrySeconds);
                return;
            }
            if (e.getStatusCode().value() == 401) {
                accessToken = null;
                accessTokenExpiresAt = LocalDateTime.MIN;
                lastAuthenticationMessage = "OpenSky rejected the Bearer token. A new token will be requested on the next refresh.";
            }
            nextRefreshAllowedAt = LocalDateTime.now().plusSeconds(refreshIntervalSeconds());
            lastFetchMessage = "OpenSky request failed with HTTP " + e.getStatusCode().value();
            log.error("Failed to fetch OpenSky flights: HTTP {}", e.getStatusCode().value());
        } catch (Exception e) {
            nextRefreshAllowedAt = LocalDateTime.now().plusSeconds(refreshIntervalSeconds());
            lastFetchMessage = "OpenSky is temporarily unavailable. Showing cached traffic.";
            log.error("Failed to fetch OpenSky flights", e);
        }
    }

    @Transactional(readOnly = true)
    public RealtimeFlightStatusDto getStatus() {
        List<RealtimeFlight> flights = realtimeFlightRepository.findAll();
        LocalDateTime lastUpdate = flights.stream()
                .map(RealtimeFlight::getLastUpdated)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        long ageSeconds = lastUpdate == null
                ? Long.MAX_VALUE
                : ChronoUnit.SECONDS.between(lastUpdate, LocalDateTime.now());
        boolean stale = lastUpdate == null || ageSeconds >= refreshIntervalSeconds() * 2L;

        return new RealtimeFlightStatusDto(
                !stale,
                stale,
                flights.size(),
                lastUpdate,
                nextRefreshAllowedAt.equals(LocalDateTime.MIN) ? null : nextRefreshAllowedAt,
                stale ? lastFetchMessage : "Live OpenSky data via " + authenticationMode(),
                authenticationMode(),
                lastAuthenticationMessage
        );
    }

    private long parseRetrySeconds(RestClientResponseException exception) {
        String value = exception.getResponseHeaders() == null
                ? null
                : exception.getResponseHeaders().getFirst("x-rate-limit-retry-after-seconds");
        try {
            return Math.max(60, Long.parseLong(value));
        } catch (Exception ignored) {
            return 15 * 60;
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

    private synchronized String getAccessToken() {
        if (!hasOAuthCredentials()) {
            lastAuthenticationMessage = "OpenSky OAuth credentials are not configured; using anonymous access.";
            return null;
        }
        if (accessToken != null && LocalDateTime.now().isBefore(accessTokenExpiresAt.minusSeconds(30))) {
            lastAuthenticationMessage = "OpenSky OAuth token is active.";
            return accessToken;
        }

        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", config.getClientId());
            form.add("client_secret", config.getClientSecret());

            Map<String, Object> response = RestClient.builder()
                    .requestFactory(openSkyRequestFactory())
                    .build()
                    .post()
                    .uri(config.getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            Object token = response == null ? null : response.get("access_token");
            if (token == null) {
                lastAuthenticationMessage = "OpenSky OAuth response did not contain an access token; using anonymous access.";
                log.warn("OpenSky OAuth response did not contain an access token. Falling back to anonymous access.");
                return null;
            }

            Object expiresInValue = response.getOrDefault("expires_in", 1800);
            long expiresIn = expiresInValue instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(expiresInValue.toString());
            accessToken = token.toString();
            accessTokenExpiresAt = LocalDateTime.now().plusSeconds(expiresIn);
            lastAuthenticationMessage = "OpenSky OAuth token is active.";
            return accessToken;
        } catch (RestClientResponseException e) {
            String errorCode = extractOAuthError(e);
            lastAuthenticationMessage = "OpenSky OAuth failed with HTTP " + e.getStatusCode().value()
                    + (errorCode == null ? "" : " (" + errorCode + ")")
                    + "; using anonymous access.";
            log.warn("Cannot obtain OpenSky OAuth token. Falling back to anonymous access: {}", lastAuthenticationMessage);
            return null;
        } catch (Exception e) {
            lastAuthenticationMessage = "OpenSky OAuth request failed; using anonymous access.";
            log.warn("Cannot obtain OpenSky OAuth token. Falling back to anonymous access: {}", e.getMessage());
            return null;
        }
    }

    private boolean hasOAuthCredentials() {
        return config.getClientId() != null && !config.getClientId().isBlank()
                && config.getClientSecret() != null && !config.getClientSecret().isBlank();
    }

    private SimpleClientHttpRequestFactory openSkyRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(7));
        return requestFactory;
    }

    private int refreshIntervalSeconds() {
        return hasOAuthCredentials()
                ? Math.max(10, config.getCacheRefreshSeconds())
                : Math.max(660, config.getAnonymousCacheRefreshSeconds());
    }

    private String authenticationMode() {
        return lastRequestUsedOAuth ? "oauth2" : "anonymous";
    }

    private String extractOAuthError(RestClientResponseException exception) {
        try {
            String body = exception.getResponseBodyAsString();
            if (body == null || body.isBlank()) return null;
            int marker = body.indexOf("\"error\"");
            if (marker < 0) return null;
            int colon = body.indexOf(':', marker);
            int firstQuote = body.indexOf('"', colon + 1);
            int secondQuote = body.indexOf('"', firstQuote + 1);
            return firstQuote >= 0 && secondQuote > firstQuote ? body.substring(firstQuote + 1, secondQuote) : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
