package com.skytrack.ai.service;

import com.skytrack.ai.entity.Airline;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AirlineService {
    List<Airline> getAllAirlines();
    Airline createAirline(Airline airline);
    Airline updateAirline(Long id, Airline airline);
    void deleteAirline(Long id);

    String updateLogo(Long id, MultipartFile file);
}