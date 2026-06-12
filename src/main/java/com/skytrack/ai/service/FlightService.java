package com.skytrack.ai.service;

import com.skytrack.ai.entity.Flight;
import java.util.List;

public interface FlightService {

    List<Flight> getAllFlights();
    Flight createFlight(Flight flight);
    Flight updateFlight(Long id, Flight flight);
    void deleteFlight(Long id);

    List<Flight> searchFlights(String query);
}