package com.skytrack.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrack.ai.entity.Airline;
import com.skytrack.ai.entity.Airport;
import com.skytrack.ai.entity.Flight;
import com.skytrack.ai.entity.FlightStatus;
import com.skytrack.ai.entity.Notification;
import com.skytrack.ai.entity.User;
import com.skytrack.ai.entity.UserRole;
import com.skytrack.ai.repository.AirlineRepository;
import com.skytrack.ai.repository.AirportRepository;
import com.skytrack.ai.repository.FlightRepository;
import com.skytrack.ai.repository.FlightSubscriptionRepository;
import com.skytrack.ai.repository.NotificationRepository;
import com.skytrack.ai.repository.UserRepository;
import com.skytrack.ai.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FlightRepository flightRepository;

    @Autowired
    private AirlineRepository airlineRepository;

    @Autowired
    private AirportRepository airportRepository;

    @Autowired
    private FlightSubscriptionRepository subscriptionRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @BeforeEach
    void clearData() {
        notificationRepository.deleteAll();
        subscriptionRepository.deleteAll();
        flightRepository.deleteAll();
        airlineRepository.deleteAll();
        airportRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registrationValidatesInputWithReadableMessage() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "",
                                "email", "not-an-email",
                                "password", "short"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void registrationAndLoginNormalizeEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "API User",
                                "email", "  Test.User@Example.com ",
                                "password", "StrongPass1!"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "TEST.USER@example.com",
                                "password", "StrongPass1!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("test.user@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void invalidLoginKeepsBackendError() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "missing@example.com",
                                "password", "WrongPass1!"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    void publicAndProtectedEndpointsHaveExpectedAccess() throws Exception {
        mockMvc.perform(get("/api/airports"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void corsAllowsConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/airports")
                        .header("Origin", "http://127.0.0.1:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:3000"));
    }

    @Test
    void adminCanCreateFlightUsingRelatedEntityIds() throws Exception {
        Airline airline = new Airline();
        airline.setCode("VN");
        airline.setName("Vietnam Airlines");
        airline = airlineRepository.save(airline);

        Airport departure = airport("SGN", "Tan Son Nhat", "Ho Chi Minh City");
        Airport arrival = airport("HAN", "Noi Bai", "Hanoi");
        departure = airportRepository.save(departure);
        arrival = airportRepository.save(arrival);

        String token = jwtUtil.generateToken("admin@example.com", "ADMIN", 60_000);
        mockMvc.perform(post("/api/flights")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "flightCode", " vn220 ",
                                "airline", Map.of("id", airline.getId()),
                                "departureAirport", Map.of("id", departure.getId()),
                                "arrivalAirport", Map.of("id", arrival.getId()),
                                "departureTime", "2026-06-15T08:00:00",
                                "arrivalTime", "2026-06-15T10:00:00",
                                "status", "SCHEDULED",
                                "type", "Direct"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightCode").value("VN220"))
                .andExpect(jsonPath("$.airline.code").value("VN"))
                .andExpect(jsonPath("$.departureAirport.code").value("SGN"))
                .andExpect(jsonPath("$.arrivalAirport.code").value("HAN"));
    }

    @Test
    void followingFlightCreatesNotificationAndUserCanManageIt() throws Exception {
        User user = userRepository.save(user("Traveler", "traveler@example.com"));
        Flight flight = flightRepository.save(flight("VN220"));
        String token = jwtUtil.generateToken(user.getEmail(), "USER", 60_000);

        mockMvc.perform(post("/api/subscriptions/me/" + flight.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flight.flightCode").value("VN220"));

        Notification notification = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId())
                .getFirst();

        mockMvc.perform(get("/api/notifications/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(notification.getId()))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("VN220")));

        String adminToken = jwtUtil.generateToken("admin@example.com", "ADMIN", 60_000);
        mockMvc.perform(put("/api/flights/" + flight.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "DELAYED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELAYED"));

        mockMvc.perform(get("/api/notifications/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Flight status updated"))
                .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("Delayed")))
                .andExpect(jsonPath("$[1].id").value(notification.getId()));

        mockMvc.perform(put("/api/notifications/me/" + notification.getId() + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/notifications/me/" + notification.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notifications/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Flight status updated"));
    }

    @Test
    void userCannotManageAnotherUsersNotification() throws Exception {
        User owner = userRepository.save(user("Owner", "owner@example.com"));
        User other = userRepository.save(user("Other", "other@example.com"));
        Notification notification = new Notification();
        notification.setUserId(owner.getId());
        notification.setTitle("Private update");
        notification.setMessage("Only the owner can manage this notification.");
        notification = notificationRepository.save(notification);
        String token = jwtUtil.generateToken(other.getEmail(), "USER", 60_000);

        mockMvc.perform(put("/api/notifications/me/" + notification.getId() + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/notifications/me/" + notification.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private Airport airport(String code, String name, String city) {
        Airport airport = new Airport();
        airport.setCode(code);
        airport.setName(name);
        airport.setCity(city);
        airport.setCountry("Vietnam");
        return airport;
    }

    private User user(String name, String email) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword("StrongPass1!");
        user.setRole(UserRole.USER);
        return user;
    }

    private Flight flight(String code) {
        Airline airline = new Airline();
        airline.setCode("VN");
        airline.setName("Vietnam Airlines");
        airline = airlineRepository.save(airline);

        Airport departure = airportRepository.save(airport("SGN", "Tan Son Nhat", "Ho Chi Minh City"));
        Airport arrival = airportRepository.save(airport("HAN", "Noi Bai", "Hanoi"));

        Flight flight = new Flight();
        flight.setFlightCode(code);
        flight.setAirline(airline);
        flight.setDepartureAirport(departure);
        flight.setArrivalAirport(arrival);
        flight.setDepartureTime(LocalDateTime.of(2026, 6, 15, 8, 0));
        flight.setArrivalTime(LocalDateTime.of(2026, 6, 15, 10, 0));
        flight.setStatus(FlightStatus.SCHEDULED);
        return flight;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
