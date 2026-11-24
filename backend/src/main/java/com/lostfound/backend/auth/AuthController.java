package com.lostfound.backend.auth;

import com.lostfound.backend.user.UserResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * The refresh token lives in a cookie that page JavaScript cannot read (HttpOnly),
     * is only sent to /api/auth (Path), and never on requests started by other sites (SameSite=Strict).
     */
    static final String REFRESH_COOKIE = "lf_refresh";

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final boolean secureCookie;

    public AuthController(AuthService authService,
                          RefreshTokenService refreshTokenService,
                          @Value("${app.jwt.refresh-cookie-secure}") boolean secureCookie) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return withRefreshCookie(authService.login(request), response);
    }

    /** Gets a new access token using the refresh cookie. The cookie is replaced by a new one. */
    @PostMapping("/refresh")
    public TokenResponse refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                                 HttpServletResponse response) {
        try {
            return withRefreshCookie(authService.refresh(refreshToken), response);
        } catch (RuntimeException e) {
            setCookie(response, "", Duration.ZERO); // a bad cookie is useless: remove it
            throw e;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                       HttpServletResponse response) {
        authService.logout(refreshToken);
        setCookie(response, "", Duration.ZERO);
    }

    private TokenResponse withRefreshCookie(AuthService.Session session, HttpServletResponse response) {
        setCookie(response, session.refreshToken(), refreshTokenService.lifetime());
        return session.accessToken();
    }

    private void setCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
