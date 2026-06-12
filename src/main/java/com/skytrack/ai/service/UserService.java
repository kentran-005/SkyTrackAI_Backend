package com.skytrack.ai.service;

import com.skytrack.ai.entity.User;
import java.util.List;
import java.util.Optional;

public interface UserService {
    List<User> getAllUsers();
    Optional<User> getUserByEmail(String email);
    User createUser(User user);
}