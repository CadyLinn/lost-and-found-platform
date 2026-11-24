package com.lostfound.backend.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ItemSearchTest {

    // National Taiwan University main library, and two points ~0.5 km and ~10 km away.
    private static final double LIB_LAT = 25.0173, LIB_LNG = 121.5397;

    @Autowired
    MockMvc mockMvc;

    /** A unique word per test, so items from other tests (same database) never show up in results. */
    private String tag;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        tag = "t" + UUID.randomUUID().toString().substring(0, 8);
        token = AuthTestHelper.registerAndLogin(mockMvc, tag + "@test.com");
    }

    private long postItem(String type, String title, String description, Double lat, Double lng, String date) throws Exception {
        String json = """
                {"type":"%s","title":"%s","description":%s,"categoryId":2,"locationText":"Campus",
                 "lat":%s,"lng":%s,"eventDate":"%s"}
                """.formatted(type, title, description == null ? "null" : "\"" + description + "\"", lat, lng, date);
        String body = mockMvc.perform(post("/api/items").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Titles returned by /api/items for the given name/value parameter pairs. */
    private List<String> titles(String... params) throws Exception {
        var request = get("/api/items");
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.content[*].title");
    }

    @Test
    void keywordMatchesPartOfChineseText() throws Exception {
        postItem("FOUND", "撿到一個咖啡色皮夾 " + tag, null, null, null, "2026-11-20");
        postItem("FOUND", "黑色雨傘 " + tag, null, null, null, "2026-11-20");

        assertThat(titles("q", "皮夾" + tag)).isEmpty(); // the text must appear exactly as typed
        assertThat(titles("q", "皮夾")).anyMatch(t -> t.contains(tag) && t.contains("皮夾"));
        assertThat(titles("q", tag, "type", "FOUND")).hasSize(2);
    }

    @Test
    void keywordSearchesDescriptionToo() throws Exception {
        postItem("LOST", "Blue backpack " + tag, "has a cat keychain " + tag + "-desc", null, null, "2026-11-20");

        assertThat(titles("q", tag + "-desc")).containsExactly("Blue backpack " + tag);
    }

    @Test
    void percentSignIsMatchedLiterally() throws Exception {
        postItem("LOST", "Card with 100% " + tag, null, null, null, "2026-11-20");
        postItem("LOST", "Card with 1000 " + tag, null, null, null, "2026-11-20");

        assertThat(titles("q", "100% " + tag)).containsExactly("Card with 100% " + tag);
    }

    @Test
    void nearFilterKeepsCloseItemsAndReportsDistance() throws Exception {
        postItem("LOST", "Near " + tag, null, LIB_LAT + 0.0045, LIB_LNG, "2026-11-20");   // ~0.5 km north
        postItem("LOST", "Far " + tag, null, LIB_LAT + 0.09, LIB_LNG, "2026-11-20");      // ~10 km north
        postItem("LOST", "Nowhere " + tag, null, null, null, "2026-11-20");               // no coordinates

        String[] query = {"q", tag, "lat", String.valueOf(LIB_LAT), "lng", String.valueOf(LIB_LNG), "radiusKm", "2"};
        assertThat(titles(query)).containsExactly("Near " + tag);
        mockMvc.perform(get("/api/items").param("q", tag).param("lat", String.valueOf(LIB_LAT))
                        .param("lng", String.valueOf(LIB_LNG)).param("radiusKm", "2"))
                .andExpect(jsonPath("$.content[0].distanceKm", closeTo(0.5, 0.05)));
    }

    @Test
    void dateRangeFilter() throws Exception {
        postItem("LOST", "Early " + tag, null, null, null, "2026-10-01");
        postItem("LOST", "Late " + tag, null, null, null, "2026-11-20");

        assertThat(titles("q", tag, "from", "2026-11-01")).containsExactly("Late " + tag);
        assertThat(titles("q", tag, "to", "2026-10-31")).containsExactly("Early " + tag);
    }

    @Test
    void incompleteLocationIsRejected() throws Exception {
        mockMvc.perform(get("/api/items?lat=25&lng=121")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/items?lat=25&lng=121&radiusKm=500")).andExpect(status().isBadRequest());
    }

    @Test
    void cachedSearchSeesNewItems() throws Exception {
        postItem("LOST", "First " + tag, null, null, null, "2026-11-20");
        assertThat(titles("q", tag)).hasSize(1); // this result is now cached

        postItem("LOST", "Second " + tag, null, null, null, "2026-11-20");
        assertThat(titles("q", tag)).hasSize(2); // posting cleared the search cache
    }

    @Test
    void cachedItemSeesEdits() throws Exception {
        long id = postItem("LOST", "Old title " + tag, null, null, null, "2026-11-20");
        mockMvc.perform(get("/api/items/" + id)).andExpect(jsonPath("$.title").value("Old title " + tag));

        String edit = """
                {"type":"LOST","title":"New title %s","categoryId":2,"locationText":"Campus","eventDate":"2026-11-20"}
                """.formatted(tag);
        mockMvc.perform(put("/api/items/" + id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(edit))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/items/" + id)).andExpect(jsonPath("$.title").value("New title " + tag));
    }
}
