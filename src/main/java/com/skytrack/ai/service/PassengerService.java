package com.skytrack.ai.service;

import com.skytrack.ai.entity.Passenger;
import java.util.List;

public interface PassengerService {
    List<Passenger> getAllPassengers();
    Passenger createPassenger(Passenger passenger);
    void deletePassenger(Long id);
}