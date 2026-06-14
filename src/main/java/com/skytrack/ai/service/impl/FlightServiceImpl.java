package com.skytrack.ai.service.impl;

import com.skytrack.ai.entity.Airline;
import com.skytrack.ai.entity.Airport;
import com.skytrack.ai.entity.Flight;
import com.skytrack.ai.exception.ResourceNotFoundException;
import com.skytrack.ai.repository.AirlineRepository;
import com.skytrack.ai.repository.AirportRepository;
import com.skytrack.ai.repository.FlightRepository;
import com.skytrack.ai.service.FlightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlightServiceImpl implements FlightService {

    private final FlightRepository flightRepository;
    private final AirlineRepository airlineRepository;
    private final AirportRepository airportRepository;

    @Value("${app.aviationstack.api-key:}")
    private String aviationStackApiKey;

    @Value("${app.aviationstack.url:http://api.aviationstack.com/v1}")
    private String aviationStackUrl;

    @Override
    public List<Flight> getAllFlights() {
        return flightRepository.findAll();
    }

    @Override
    public Flight createFlight(Flight flight) {
        return flightRepository.save(flight);
    }

    @Override
    public Flight updateFlight(Long id, Flight flight) {
        Flight existing = flightRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found"));

        if (flight.getFlightCode() != null) existing.setFlightCode(flight.getFlightCode().trim());
        if (flight.getDepartureTime() != null) existing.setDepartureTime(flight.getDepartureTime());
        if (flight.getArrivalTime() != null) existing.setArrivalTime(flight.getArrivalTime());
        if (flight.getStatus() != null) existing.setStatus(flight.getStatus());
        if (flight.getPrice() != null) existing.setPrice(flight.getPrice());
        if (flight.getType() != null) existing.setType(flight.getType());
        if (flight.getGate() != null) existing.setGate(flight.getGate());
        if (flight.getTerminal() != null) existing.setTerminal(flight.getTerminal());

        if (flight.getAirline() != null && flight.getAirline().getId() != null) {
            existing.setAirline(airlineRepository.findById(flight.getAirline().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Airline not found")));
        }
        if (flight.getDepartureAirport() != null && flight.getDepartureAirport().getId() != null) {
            existing.setDepartureAirport(airportRepository.findById(flight.getDepartureAirport().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Departure airport not found")));
        }
        if (flight.getArrivalAirport() != null && flight.getArrivalAirport().getId() != null) {
            existing.setArrivalAirport(airportRepository.findById(flight.getArrivalAirport().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Arrival airport not found")));
        }

        return flightRepository.save(existing);
    }

    @Override
    public void deleteFlight(Long id) {
        flightRepository.deleteById(id);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Flight> searchFlights(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        // 1. ƯU TIÊN: Tìm trong Database nội bộ trước
        List<Flight> localFlights = flightRepository.searchFlights(normalizedQuery);
        if (!localFlights.isEmpty()) {
            log.info("✅ Tìm thấy {} chuyến bay trong Database nội bộ với từ khóa: {}", localFlights.size(), normalizedQuery);
            return localFlights;
        }

        // 2. FALLBACK: Nếu DB không có, gọi AviationStack API
        log.warn("⚠️ Không tìm thấy '{}' trong DB. Đang gọi AviationStack API...", normalizedQuery);

        if (aviationStackApiKey == null || aviationStackApiKey.isBlank()) {
            log.error("❌ LỖI: Chưa cấu hình app.aviationstack.api-key trong application.yml!");
            return new ArrayList<>();
        }

        try {
            // Bản Free bắt buộc phải dùng HTTP (không phải HTTPS)
            // Bản Free chỉ tìm được theo flight_iata (Mã chuyến bay VN220)
            String url = UriComponentsBuilder.fromUriString(aviationStackUrl + "/flights")
                    .queryParam("access_key", aviationStackApiKey)
                    .queryParam("flight_iata", normalizedQuery)
                    .encode()
                    .build()
                    .toUriString();

            RestClient restClient = RestClient.create();
            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);

            List<Flight> aviationFlights = new ArrayList<>();

            if (response != null && response.containsKey("data")) {
                List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.get("data");

                // Kiểm tra xem API có trả về lỗi không (Ví dụ giới hạn request)
                if (dataList.isEmpty()) {
                    log.warn("⚠️ AviationStack trả về mảng rỗng (Có thể mã chuyến bay không tồn tại hoặc bạn dùng giới hạn Free plan).");
                    return new ArrayList<>();
                }

                for (Map<String, Object> item : dataList) {
                    Flight flight = new Flight();

                    // Lấy thông tin Flight
                    Map<String, Object> flightInfo = (Map<String, Object>) item.get("flight");
                    if (flightInfo != null) {
                        flight.setFlightCode((String) flightInfo.getOrDefault("iata", "N/A"));
                    }

                    Map<String, Object> departure = (Map<String, Object>) item.get("departure");
                    Map<String, Object> arrival = (Map<String, Object>) item.get("arrival");
                    Map<String, Object> airlineInfo = (Map<String, Object>) item.get("airline");

                    // Map thời gian
                    if (departure != null) {
                        flight.setDepartureTime(parseAviationStackTime((String) departure.get("scheduled")));
                        flight.setTerminal((String) departure.get("terminal"));
                        flight.setGate((String) departure.get("gate"));
                    }
                    if (arrival != null) flight.setArrivalTime(parseAviationStackTime((String) arrival.get("scheduled")));

                    // Map Status
                    String astStatus = (String) item.getOrDefault("flight_status", "scheduled");
                    flight.setStatus(mapAviationStackStatus(astStatus));

                    // 🔴 QUAN TRỌNG: Bọc dữ liệu String thành Object để Frontend không bị lỗi undefined
                    // Vì AviationStack chỉ trả về String "SGN", nhưng Frontend cần object departureAirport.code
                    if (departure != null) {
                        Airport depAirport = new Airport();
                        depAirport.setCode((String) departure.getOrDefault("iata", "N/A"));
                        depAirport.setName((String) departure.getOrDefault("airport", "Unknown Airport"));
                        depAirport.setCity(""); // API free không có city
                        flight.setDepartureAirport(depAirport);
                    }

                    if (arrival != null) {
                        Airport arrAirport = new Airport();
                        arrAirport.setCode((String) arrival.getOrDefault("iata", "N/A"));
                        arrAirport.setName((String) arrival.getOrDefault("airport", "Unknown Airport"));
                        arrAirport.setCity("");
                        flight.setArrivalAirport(arrAirport);
                    }

                    if (airlineInfo != null) {
                        Airline airline = new Airline();
                        airline.setCode((String) airlineInfo.getOrDefault("iata", "N/A"));
                        airline.setName((String) airlineInfo.getOrDefault("name", "Unknown Airline"));
                        flight.setAirline(airline);
                    }

                    // Set ID âm để phân biệt đây là data tạm thời từ API, không phải từ DB
                    flight.setId(-(long) (aviationFlights.size() + 1));
                    flight.setType("Realtime API");

                    aviationFlights.add(flight);
                }
                log.info("✈️ Lấy được {} chuyến bay từ AviationStack", aviationFlights.size());
            } else {
                log.error("❌ Lỗi gọi AviationStack: Response null hoặc không có key 'data'. Response: {}", response);
            }

            return aviationFlights;

        } catch (Exception e) {
            log.error("❌ LỖI KẾT NỐI AviationStack API: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private LocalDateTime parseAviationStackTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException e) {
                log.warn("⚠️ Không parse được thời gian AviationStack: {}", value);
                return null;
            }
        }
    }

    // Hàm chuyển đổi Status của AviationStack sang Enum
    private com.skytrack.ai.entity.FlightStatus mapAviationStackStatus(String status) {
        if (status == null) return com.skytrack.ai.entity.FlightStatus.SCHEDULED;
        return switch (status.toLowerCase()) {
            case "active" -> com.skytrack.ai.entity.FlightStatus.ON_TIME;
            case "landed" -> com.skytrack.ai.entity.FlightStatus.SCHEDULED; // Đã hạ cánh coi như xong
            case "cancelled" -> com.skytrack.ai.entity.FlightStatus.CANCELLED;
            case "delayed" -> com.skytrack.ai.entity.FlightStatus.DELAYED;
            default -> com.skytrack.ai.entity.FlightStatus.SCHEDULED;
        };
    }
}
