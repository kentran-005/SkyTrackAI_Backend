package com.skytrack.ai.service;

import com.skytrack.ai.entity.Airport;
import java.util.List;

public interface AirportService {
    List<Airport> getAllAirports();
    Airport createAirport(Airport airport);
    Airport updateAirport(Long id, Airport airport);
    void deleteAirport(Long id);
}
