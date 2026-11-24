package com.lostfound.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lostfound.backend.TestcontainersConfiguration;
import com.lostfound.backend.user.User;
import com.lostfound.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    private ResultActions register(String email, String password, String displayName) throws Exception {
        String json = """
                {"email":"%s","password":"%s","displayName":"%s"}
                """.formatted(email, password, displayName);
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void registerCreatesUserWithHashedPassword() throws Exception {
        register("new@test.com", "password123", "New User")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@test.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User saved = userRepository.findByEmail("new@test.com").orElseThrow();
        assertThat(saved.getPasswordHash())
                .startsWith("$2a$")
                .isNotEqualTo("password123");
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        register("dup@test.com", "password123", "First").andExpect(status().isCreated());
        register("dup@test.com", "password123", "Second").andExpect(status().isConflict());
    }

    @Test
    void invalidInputReturns400() throws Exception {
        register("not-an-email", "123", "Bad").andExpect(status().isBadRequest());
    }
}