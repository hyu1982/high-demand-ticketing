package demo.highdemand.ticketing.service;

import demo.highdemand.ticketing.dto.UserDto;
import demo.highdemand.ticketing.entity.User;
import demo.highdemand.ticketing.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public UserDto createUser(
            String username,
            String password,
            String email,
            String role,
            String displayName) {

        User user = new User();

        user.setUsername(username);

        // Hash the password before storing it
        user.setPassword(
                passwordEncoder.encode(password)
        );

        user.setEmail(email);
        user.setRole(role);
        user.setEnabled(true);
        user.setDisplayName(displayName);

        User savedUser = userRepository.save(user);

        return toDto(savedUser);
    }

    public void changePassword(
            String username,
            String newPassword) {

        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"
                        )
                );

        user.setPassword(passwordEncoder.encode(newPassword));

        userRepository.save(user);
    }

    public boolean verifyPassword(
            String username,
            String password) {

        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"
                        )
                );

        return passwordEncoder.matches(
                password,
                user.getPassword()
        );
    }

    private UserDto toDto(User user) {

        UserDto dto = new UserDto();

        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setEnabled(user.isEnabled());
        dto.setDisplayName(user.getDisplayName());

        return dto;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDto> getAllUsers() {

        return userRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }
}