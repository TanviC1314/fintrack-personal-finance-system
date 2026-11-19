package com.fintrack.user.service;

import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.user.entity.User;
import com.fintrack.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getById(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
