package com.lostfound.backend.item;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.lostfound.backend.AuthTestHelper;
import com.lostfound.backend.TestcontainersConfiguration;
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
class ItemControllerTest {

    // Category 1 = "Phone", inserted by V1__init.sql
    private static final String PHONE_ITEM = """
            {
              "type": "LOST",
              "title": "Black iPhone",
              "description": "Lost near the library entrance",
              "categoryId": 1,
              "locationText": "Main library",
              "lat": 25.0173,
              "lng": 121.5397,
              "eventDate": "2026-11-20",
              "verificationQuestion": "What colour is the case?"
            }
            """;

    @Autowired
    MockMvc mockMvc;

    private ResultActions createItem(String token, String json) throws Exception {
        return mockMvc.perform(post("/api/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private long createItemAndGetId(String token) throws Exception {
        String body = createItem(token, PHONE_ITEM)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void createRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON).content(PHONE_ITEM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createReturnsItemWithOwnerAndCategory() throws Exception {
        String token = AuthTestHelper.registerAndLogin(mockMvc, "item-create@test.com");

        createItem(token, PHONE_ITEM)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Black iPhone"))
                .andExpect(jsonPath("$.type").value("LOST"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.categoryName").value("Phone"))
                .andExpect(jsonPath("$.ownerDisplayName").value("Tester"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void createWithInvalidDataReturns400() throws Exception {
        String token = AuthTestHelper.registerAndLogin(mockMvc, "item-invalid@test.com");
        String missingTitleAndFutureDate = """
                {"type":"LOST","title":"","categoryId":1,"locationText":"Library","eventDate":"2999-01-01"}
                """;

        createItem(token, missingTitleAndFutureDate).andExpect(status().isBadRequest());
    }

    @Test
    void todayCountsAsTodayInEveryTimezone() throws Exception {
        // When it's already "tomorrow" in Asia but still "today" in UTC, a post dated with the
        // user's local today must be accepted (this used to fail between midnight and 8 a.m. in Taiwan).
        String token = AuthTestHelper.registerAndLogin(mockMvc, "item-tz@test.com");
        java.time.LocalDate earliestToday = java.time.LocalDate.now(java.time.ZoneOffset.ofHours(14));

        createItem(token, PHONE_ITEM.replace("2026-11-20", earliestToday.toString()))
                .andExpect(status().isCreated());
        createItem(token, PHONE_ITEM.replace("2026-11-20", earliestToday.plusDays(1).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithUnknownCategoryReturns400() throws Exception {
        String token = AuthTestHelper.registerAndLogin(mockMvc, "item-badcat@test.com");

        createItem(token, PHONE_ITEM.replace("\"categoryId\": 1", "\"categoryId\": 999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anyoneCanViewAndSearchItems() throws Exception {
        String token = AuthTestHelper.registerAndLogin(mockMvc, "item-view@test.com");
        long id = createItemAndGetId(token);

        mockMvc.perform(get("/api/items/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mockMvc.perform(get("/api/items").param("type", "LOST").param("categoryId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[*].type", everyItem(is("LOST"))));

        mockMvc.perform(get("/api/items").param("type", "FOUND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].type", everyItem(is("FOUND"))));
    }

    @Test
    void categoriesArePublic() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Phone"))
                .andExpect(jsonPath("$.length()").value(6));
    }

    @Test
    void errorsExplainWhatWentWrong() throws Exception {
        mockMvc.perform(get("/api/items/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Item not found"));
    }

    @Test
    void missingItemReturns404() throws Exception {
        mockMvc.perform(get("/api/items/999999")).andExpect(status().isNotFound());
    }

    @Test
    void ownerCanUpdateItem() throws Exception {
        String token = AuthTestHelper.registerAndLogin(mockMvc, "item-update@test.com");
        long id = createItemAndGetId(token);

        mockMvc.perform(put("/api/items/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PHONE_ITEM.replace("Black iPhone", "Black iPhone 15")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Black iPhone 15"));
    }

    @Test
    void otherUserCannotUpdateOrDeleteItem() throws Exception {
        String ownerToken = AuthTestHelper.registerAndLogin(mockMvc, "item-owner@test.com");
        String otherToken = AuthTestHelper.registerAndLogin(mockMvc, "item-other@test.com");
        long id = createItemAndGetId(ownerToken);

        mockMvc.perform(put("/api/items/" + id)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PHONE_ITEM))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/items/" + id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanDeleteItem() throws Exception {
        String token = AuthTestHelper.registerAndLogin(mockMvc, "item-delete@test.com");
        long id = createItemAndGetId(token);

        mockMvc.perform(delete("/api/items/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/items/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void myItemsListsOnlyMyPosts() throws Exception {
        String mine = AuthTestHelper.registerAndLogin(mockMvc, "item-mine@test.com");
        String other = AuthTestHelper.registerAndLogin(mockMvc, "item-notmine@test.com");
        createItemAndGetId(mine);
        createItemAndGetId(mine);
        createItemAndGetId(other);

        mockMvc.perform(get("/api/me/items").header("Authorization", "Bearer " + mine))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }
}
