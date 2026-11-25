package com.lostfound.backend.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.lostfound.backend.AuthTestHelper;
import com.lostfound.backend.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ModerationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private String tag;
    private String author;
    private String reporter;
    private String admin;
    private long itemId;

    @BeforeEach
    void setUp() throws Exception {
        tag = "mod" + UUID.randomUUID().toString().substring(0, 8);
        author = AuthTestHelper.registerAndLogin(mockMvc, tag + "-author@test.com");
        reporter = AuthTestHelper.registerAndLogin(mockMvc, tag + "-reporter@test.com");
        admin = newAdmin(tag + "-admin@test.com");

        String body = mockMvc.perform(post("/api/items").header("Authorization", "Bearer " + author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"FOUND","title":"Cheap watches %s","categoryId":6,"locationText":"Campus",
                                 "lat":25.0173,"lng":121.5397,"eventDate":"2026-11-20"}
                                """.formatted(tag)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        itemId = ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Same as scripts/make-admin.sh: register normally, then promote in the database, then log in. */
    private String newAdmin(String email) throws Exception {
        AuthTestHelper.registerAndLogin(mockMvc, email);
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", email);
        return login(email);
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions as(String token, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions report(String token, String reason) throws Exception {
        return as(token, post("/api/items/" + itemId + "/reports").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"%s\",\"details\":\"Looks like an ad\"}".formatted(reason)));
    }

    private List<String> searchTitles() throws Exception {
        String body = mockMvc.perform(get("/api/items").param("q", tag)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.content[*].title");
    }

    @Test
    void usersCanReportOnceButNotTheirOwnPost() throws Exception {
        report(reporter, "SPAM").andExpect(status().isCreated());
        report(reporter, "SPAM").andExpect(status().isConflict());
        report(author, "SPAM").andExpect(status().isForbidden());
        report(reporter, "NOT_A_REASON").andExpect(status().isBadRequest());
    }

    @Test
    void normalUsersCannotUseAdminPages() throws Exception {
        as(reporter, get("/api/admin/reports")).andExpect(status().isForbidden());
        as(reporter, post("/api/admin/items/" + itemId + "/hide")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminSeesReportsAndHidingRemovesThePostFromPublicView() throws Exception {
        report(reporter, "SCAM").andExpect(status().isCreated());
        as(admin, get("/api/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.itemId == %d)].reason".formatted(itemId)).value("SCAM"));
        assertThat(searchTitles()).hasSize(1);

        as(admin, post("/api/admin/items/" + itemId + "/hide").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Advertising\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(true));

        // Gone for the public: search, detail, matches and claims.
        assertThat(searchTitles()).isEmpty();
        mockMvc.perform(get("/api/items/" + itemId)).andExpect(status().isNotFound());
        as(reporter, get("/api/items/" + itemId)).andExpect(status().isNotFound());
        as(reporter, post("/api/items/" + itemId + "/claims").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"mine\"}")).andExpect(status().isNotFound());

        // Still visible to its author (with the reason) and to admins.
        as(author, get("/api/items/" + itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(true))
                .andExpect(jsonPath("$.hiddenReason").value("Advertising"));
        as(admin, get("/api/items/" + itemId)).andExpect(status().isOk());

        // The author was told, and the report is now resolved.
        as(author, get("/api/notifications")).andExpect(jsonPath("$.notifications[0].type").value("ITEM_HIDDEN"));
        as(admin, get("/api/admin/reports").param("status", "RESOLVED"))
                .andExpect(jsonPath("$[?(@.itemId == %d)].handledByDisplayName".formatted(itemId)).value("Tester"));
        as(admin, get("/api/admin/items/hidden"))
                .andExpect(jsonPath("$[?(@.id == %d)].title".formatted(itemId)).exists());
    }

    @Test
    void unhideBringsThePostBack() throws Exception {
        as(admin, post("/api/admin/items/" + itemId + "/hide")).andExpect(status().isOk());
        as(admin, post("/api/admin/items/" + itemId + "/hide")).andExpect(status().isConflict());

        as(admin, post("/api/admin/items/" + itemId + "/unhide"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(false));

        assertThat(searchTitles()).hasSize(1);
        mockMvc.perform(get("/api/items/" + itemId)).andExpect(status().isOk());
    }

    @Test
    void dismissingClosesReportsButKeepsThePost() throws Exception {
        report(reporter, "OTHER").andExpect(status().isCreated());

        as(admin, post("/api/admin/items/" + itemId + "/dismiss-reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dismissed").value(1));

        as(admin, get("/api/admin/reports"))
                .andExpect(jsonPath("$[?(@.itemId == %d)]".formatted(itemId)).isEmpty());
        mockMvc.perform(get("/api/items/" + itemId)).andExpect(status().isOk());
    }

    @Test
    void adminRightsComeFromTheDatabaseAtLogin() throws Exception {
        String email = tag + "-promoted@test.com";
        String before = AuthTestHelper.registerAndLogin(mockMvc, email);
        as(before, get("/api/admin/stats")).andExpect(status().isForbidden());

        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", email);
        // The old token still says USER until the next login.
        as(before, get("/api/admin/stats")).andExpect(status().isForbidden());

        as(login(email), get("/api/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isNumber())
                .andExpect(jsonPath("$.openReports").isNumber());
    }
}
