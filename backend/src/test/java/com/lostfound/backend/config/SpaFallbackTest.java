package com.lostfound.backend.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lostfound.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** The React app packaged in the jar (src/test/resources/static stands in for the real build). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SpaFallbackTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void homePageIsTheApp() throws Exception {
        // Spring Boot's "welcome page" answers "/" with an internal forward to index.html.
        // MockMvc records forwards instead of running them; a real server runs it.
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    void appPagesReturnIndexHtmlWithoutLoggingIn() throws Exception {
        for (String path : new String[] {"/items/5", "/me/items", "/admin", "/login"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<title>test app</title>")))
                    .andExpect(header().string("Cache-Control", "no-cache"));
        }
    }

    @Test
    void builtAssetsAreCachedForAYear() throws Exception {
        mockMvc.perform(get("/assets/app-abc123.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("immutable")));
    }

    @Test
    void unknownApiUrlsStay404AndApiStaysProtected() throws Exception {
        mockMvc.perform(get("/api/does-not-exist")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/categories")).andExpect(status().isOk());
    }

    @Test
    void pagesCarrySecurityHeaders() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
