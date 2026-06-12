package com.skytrack.ai.service.impl;

import com.skytrack.ai.entity.Airport;
import com.skytrack.ai.exception.ResourceNotFoundException;
import com.skytrack.ai.repository.AirportRepository;
import com.skytrack.ai.service.AirportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AirportServiceImpl implements AirportService {

    private final AirportRepository airportRepository;

    @Override
    public List<Airport> getAllAirports() {
        return airportRepository.findAll();
    }

    @Override
    public Airport createAirport(Airport airport) {
        return airportRepository.save(airport);
    }

    @Override
    public Airport updateAirport(Long id, Airport airport) {
        Airport existing = airportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Airport not found"));
        existing.setCode(airport.getCode());
        existing.setName(airport.getName());
        existing.setCity(airport.getCity());
        existing.setCountry(airport.getCountry());
        existing.setLatitude(airport.getLatitude());
        existing.setLongitude(airport.getLongitude());
        return airportRepository.save(existing);
    }

    @Override
    public void deleteAirport(Long id) {
        if (!airportRepository.existsById(id)) {
            throw new ResourceNotFoundException("Airport not found");
        }
        airportRepository.deleteById(id);
    }
}
