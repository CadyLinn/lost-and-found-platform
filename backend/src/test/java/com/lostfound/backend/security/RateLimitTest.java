package com.lostfound.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lostfound.backend.TestcontainersConfiguration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.login-per-minute=3",
        "app.rate-limit.max-failed-logins=3"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitTest {

    /** Every call gets its own fake IP unless a test picks one, so IP limits don't interfere. */
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    MockMvc mockMvc;

    private static String freshIp() {
        int n = NEXT_IP.getAndIncrement();
        return "10.0." + (n / 250) + "." + (n % 250 + 1);
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private String registerUser() throws Exception {
        String email = "limit-" + UUID.randomUUID() + "@test.com";
        mockMvc.perform(post("/api/auth/register")
                        .with(request -> {
                            request.setRemoteAddr(freshIp());
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"displayName\":\"L\"}".formatted(email)))
                .andExpect(status().isCreated());
        return email;
    }

    @Test
    void oneIpIsLimitedButOthersAreNot() throws Exception {
        String ip = freshIp();
        for (int i = 0; i < 3; i++) {
            login("nobody-" + i + "@test.com", "wrong-password", ip).andExpect(status().isUnauthorized());
        }

        login("nobody-x@test.com", "wrong-password", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail").value("Too many requests, please try again later"));

        login("nobody-y@test.com", "wrong-password", freshIp()).andExpect(status().isUnauthorized());
    }

    @Test
    void accountLocksAfterTooManyWrongPasswordsFromAnywhere() throws Exception {
        String victim = registerUser();
        String bystander = registerUser();

        // Three wrong guesses from three different machines.
        for (int i = 0; i < 3; i++) {
            login(victim, "guess-" + i, freshIp()).andExpect(status().isUnauthorized());
        }

        // Now even the right password is refused for a while…
        login(victim, "password123", freshIp())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail").value("Too many failed logins, please try again later"));
        // …but other accounts are unaffected.
        login(bystander, "password123", freshIp()).andExpect(status().isOk());
    }

    @Test
    void successfulLoginResetsTheFailureCount() throws Exception {
        String email = registerUser();

        login(email, "wrong-1", freshIp()).andExpect(status().isUnauthorized());
        login(email, "wrong-2", freshIp()).andExpect(status().isUnauthorized());
        login(email, "password123", freshIp()).andExpect(status().isOk());
        login(email, "wrong-3", freshIp()).andExpect(status().isUnauthorized());
        login(email, "wrong-4", freshIp()).andExpect(status().isUnauthorized());

        login(email, "password123", freshIp()).andExpect(status().isOk());
    }
}
