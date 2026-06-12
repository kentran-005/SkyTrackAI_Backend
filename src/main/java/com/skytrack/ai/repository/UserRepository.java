package com.skytrack.ai.repository;

import com.skytrack.ai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // Hàm này dùng để tìm user theo email lúc đăng nhập (Login)
    Optional<User> findByEmail(String email);
}