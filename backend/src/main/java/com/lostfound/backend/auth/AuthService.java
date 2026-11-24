package com.lostfound.backend.auth;

import com.lostfound.backend.user.User;
import com.lostfound.backend.user.UserRepository;
import com.lostfound.backend.user.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = new User(email, passwordHash, request.displayName().trim());
        User saved = userRepository.save(user);

        return UserResponse.from(saved);
    }

    /** A short-lived access token for the response body, and a long-lived refresh token for the cookie. */
    public record Session(TokenResponse accessToken, String refreshToken) {
    }

    @Transactional(readOnly = true)
    public Session login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        return new Session(tokenService.createToken(user), refreshTokenService.issue(user.getId()));
    }

    /** Swaps a refresh token for a new access token and a new refresh token. The old one stops working. */
    @Transactional(readOnly = true)
    public Session refresh(String refreshToken) {
        User user = refreshTokenService.consume(refreshToken)
                .flatMap(userRepository::findById)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Session expired, please log in again"));

        return new Session(tokenService.createToken(user), refreshTokenService.issue(user.getId()));
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }
}
