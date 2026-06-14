package com.skytrack.ai.service;

import com.skytrack.ai.entity.User;
import java.util.List;
import java.util.Optional;

public interface UserService {
    List<User> getAllUsers();
    User getUserById(Long id);
    Optional<User> getUserByEmail(String email);
    User createUser(User user);
    void deleteUser(Long id);
}
