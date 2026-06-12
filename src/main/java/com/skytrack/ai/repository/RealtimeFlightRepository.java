package com.skytrack.ai.repository;

import com.skytrack.ai.entity.RealtimeFlight;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RealtimeFlightRepository extends JpaRepository<RealtimeFlight, String> {
}