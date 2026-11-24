package com.lostfound.backend.match;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MatchApiTest {

    @Autowired
    MockMvc mockMvc;

    /** Each test uses its own month, far from other tests' dates, so their posts never become candidates. */
    private long postItem(String token, String type, int categoryId, String title, double lat, String date)
            throws Exception {
        String json = """
                {"type":"%s","title":"%s","categoryId":%d,"locationText":"總圖書館",
                 "lat":%s,"lng":121.5397,"eventDate":"%s"}
                """.formatted(type, title, categoryId, lat, date);
        String body = mockMvc.perform(post("/api/items").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String newUser() throws Exception {
        return AuthTestHelper.registerAndLogin(mockMvc, "match-" + UUID.randomUUID() + "@test.com");
    }

    @Test
    void suggestsTheBestOppositePostsFirst() throws Exception {
        String loser = newUser();
        String finder = newUser();
        long lost = postItem(loser, "LOST", 1, "黑色 iPhone 貓咪貼紙", 25.0173, "2025-03-10");
        long goodMatch = postItem(finder, "FOUND", 1, "撿到黑色 iPhone 有貓咪貼紙", 25.0176, "2025-03-11");
        long weakMatch = postItem(finder, "FOUND", 1, "一支手機", 25.0500, "2025-03-25");
        postItem(finder, "FOUND", 2, "咖啡色皮夾", 25.0900, "2025-02-01");   // wrong category, far, weeks earlier
        postItem(loser, "FOUND", 1, "自己的黑色 iPhone", 25.0173, "2025-03-10"); // same owner: never suggested

        String body = mockMvc.perform(get("/api/items/" + lost + "/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].item.id").value(goodMatch))
                .andExpect(jsonPath("$[0].reasons.length()").value(4))
                .andReturn().getResponse().getContentAsString();

        List<Integer> ids = JsonPath.read(body, "$[*].item.id");
        List<Integer> scores = JsonPath.read(body, "$[*].score");
        assertThat(ids).hasSize(2).containsExactly((int) goodMatch, (int) weakMatch);
        assertThat(scores.get(0)).isGreaterThan(scores.get(1));
    }

    @Test
    void strongNewMatchNotifiesTheOtherPoster() throws Exception {
        String loser = newUser();
        String finder = newUser();
        postItem(loser, "LOST", 3, "一串鑰匙 藍色吊飾", 25.0173, "2025-06-10");
        long found = postItem(finder, "FOUND", 3, "撿到鑰匙 有藍色吊飾", 25.0175, "2025-06-10");

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + loser))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.notifications[0].type").value("MATCH_FOUND"))
                .andExpect(jsonPath("$.notifications[0].itemId").value(found));
        // The finder isn't told about their own post.
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + finder))
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void unknownItemIs404() throws Exception {
        mockMvc.perform(get("/api/items/999999/matches")).andExpect(status().isNotFound());
    }
}
