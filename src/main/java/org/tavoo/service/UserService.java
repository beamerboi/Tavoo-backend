package org.tavoo.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tavoo.dto.CreateUserRequest;
import org.tavoo.dto.UserResponse;
import org.tavoo.entity.User;
import org.tavoo.exception.BusinessRuleException;
import org.tavoo.exception.ResourceNotFoundException;
import org.tavoo.repository.UserRepository;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsers() {
        return userRepository.findAllByOrderByUsernameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BusinessRuleException("Username '" + username + "' already exists");
        }
        User user = new User(
                username,
                passwordEncoder.encode(request.password()),
                request.role()
        );
        return toResponse(userRepository.save(user));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole());
    }
}
