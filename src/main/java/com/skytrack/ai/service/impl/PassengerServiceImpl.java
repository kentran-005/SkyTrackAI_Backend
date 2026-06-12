package com.skytrack.ai.service.impl;

import com.skytrack.ai.entity.Passenger;
import com.skytrack.ai.exception.ResourceNotFoundException;
import com.skytrack.ai.repository.PassengerRepository;
import com.skytrack.ai.service.PassengerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PassengerServiceImpl implements PassengerService {

    private final PassengerRepository passengerRepository;

    @Override
    public List<Passenger> getAllPassengers() {
        return passengerRepository.findAll();
    }

    @Override
    public Passenger createPassenger(Passenger passenger) {
        return passengerRepository.save(passenger);
    }

    @Override
    public void deletePassenger(Long id) {
        if (!passengerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Passenger not found");
        }
        passengerRepository.deleteById(id);
    }
}
