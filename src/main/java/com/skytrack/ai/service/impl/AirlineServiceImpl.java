package com.skytrack.ai.service.impl;

import com.skytrack.ai.entity.Airline;
import com.skytrack.ai.exception.ResourceNotFoundException;
import com.skytrack.ai.repository.AirlineRepository;
import com.skytrack.ai.service.AirlineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AirlineServiceImpl implements AirlineService {

    private final AirlineRepository airlineRepository;

    @Override
    public List<Airline> getAllAirlines() {
        return airlineRepository.findAll();
    }

    @Override
    public Airline createAirline(Airline airline) {
        return airlineRepository.save(airline);
    }

    @Override
    public Airline updateAirline(Long id, Airline airline) {
        Airline existing = airlineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Airline not found"));
        existing.setCode(airline.getCode());
        existing.setName(airline.getName());
        existing.setLogo(airline.getLogo());
        return airlineRepository.save(existing);
    }

    @Override
    public void deleteAirline(Long id) {
        if (!airlineRepository.existsById(id)) {
            throw new ResourceNotFoundException("Airline not found");
        }
        airlineRepository.deleteById(id);
    }
}
