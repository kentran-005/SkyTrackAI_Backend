package com.skytrack.ai.repository;

import com.skytrack.ai.entity.FlightSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface FlightSubscriptionRepository extends JpaRepository<FlightSubscription, Long> {
    List<FlightSubscription> findByUserId(Long userId);
    boolean existsByUserIdAndFlightId(Long userId, Long flightId);

    @Transactional
    void deleteByUserIdAndFlightId(Long userId, Long flightId);
}
