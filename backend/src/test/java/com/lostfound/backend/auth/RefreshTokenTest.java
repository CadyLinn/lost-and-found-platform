package com.lostfound.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.lostfound.backend.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefreshTokenTest {

    @Autowired
    MockMvc mockMvc;

    private String email;

    @BeforeEach
    void register() throws Exception {
        email = "refresh-" + UUID.randomUUID() + "@test.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"displayName\":\"R\"}".formatted(email)))
                .andExpect(status().isCreated());
    }

    private MvcResult login() throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(AuthController.REFRESH_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private MvcResult refresh(Cookie cookie) throws Exception {
        var request = post("/api/auth/refresh");
        if (cookie != null) request.cookie(cookie);
        return mockMvc.perform(request).andReturn();
    }

    @Test
    void loginSetsASafeRefreshCookie() throws Exception {
        MvcResult result = login();
        Cookie cookie = refreshCookie(result);

        assertThat(cookie.getValue()).hasSizeGreaterThan(40);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("SameSite=Strict");
        // The refresh token must never be in the JSON body, where page scripts could read it.
        assertThat(result.getResponse().getContentAsString()).doesNotContain(cookie.getValue());
    }

    @Test
    void refreshGivesAWorkingAccessTokenAndRotatesTheCookie() throws Exception {
        Cookie first = refreshCookie(login());

        MvcResult refreshed = refresh(first);
        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        String accessToken = JsonPath.read(refreshed.getResponse().getContentAsString(), "$.accessToken");
        Cookie second = refreshCookie(refreshed);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void aRefreshTokenWorksOnlyOnce() throws Exception {
        Cookie first = refreshCookie(login());

        assertThat(refresh(first).getResponse().getStatus()).isEqualTo(200);
        MvcResult reused = refresh(first);
        assertThat(reused.getResponse().getStatus()).isEqualTo(401);
        // The useless cookie is cleared.
        assertThat(reused.getResponse().getCookie(AuthController.REFRESH_COOKIE).getMaxAge()).isZero();
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        Cookie cookie = refreshCookie(login());

        mockMvc.perform(post("/api/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AuthController.REFRESH_COOKIE, 0));
        assertThat(refresh(cookie).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void refreshWithoutOrWithFakeCookieIs401() throws Exception {
        assertThat(refresh(null).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh(new Cookie(AuthController.REFRESH_COOKIE, "made-up")).getResponse().getStatus())
                .isEqualTo(401);
    }
}
