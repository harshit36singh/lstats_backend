package com.example.lstats.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.lstats.auth.dto.AuthResponse;
import com.example.lstats.auth.dto.LoginRequest;
import com.example.lstats.auth.dto.RegisterRequest;
import com.example.lstats.auth.util.Jwtutil;
import com.example.lstats.model.User;
import com.example.lstats.repository.UserRepository;
import jakarta.transaction.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        if (req.getEmail() != null && userRepository.existsByEmail(req.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        String hashedPassword = passwordEncoder.encode(req.getPassword());
        User user = User.builder().username(req.getUsername()).password(hashedPassword).email(req.getEmail())
                .collegename(req.getCollegename()).build();
        userRepository.save(user);
        return new AuthResponse("ascac", null);

    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User Not found"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalud credentials");
        }
        String token = Jwtutil.generateToken(user.getUsername());
        return new AuthResponse("Login Successful", token);
    }
}
