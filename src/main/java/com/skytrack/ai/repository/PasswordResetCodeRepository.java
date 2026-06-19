package com.skytrack.ai.repository;

import com.skytrack.ai.entity.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {
    Optional<PasswordResetCode> findTopByEmailAndUsedFalseOrderByCreatedAtDesc(String email);
}
