package com.lostfound.backend.claim;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.lostfound.backend.AuthTestHelper;
import com.lostfound.backend.TestcontainersConfiguration;
import java.util.concurrent.atomic.AtomicInteger;
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
class ClaimFlowTest {

    private static final AtomicInteger USER_COUNTER = new AtomicInteger();

    private static final String FOUND_WALLET = """
            {"type":"FOUND","title":"Brown wallet","categoryId":2,"locationText":"Cafeteria",
             "eventDate":"2026-11-20","verificationQuestion":"How many cards are inside?"}
            """;

    @Autowired
    MockMvc mockMvc;

    private String newUser() throws Exception {
        return AuthTestHelper.registerAndLogin(mockMvc, "claim-user-" + USER_COUNTER.incrementAndGet() + "@test.com");
    }

    private long postItem(String token) throws Exception {
        String body = mockMvc.perform(post("/api/items").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(FOUND_WALLET))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions claim(String token, long itemId, String answer) throws Exception {
        String json = answer == null
                ? "{\"message\":\"I think this is mine\"}"
                : "{\"answer\":\"%s\",\"message\":\"I think this is mine\"}".formatted(answer);
        return mockMvc.perform(post("/api/items/" + itemId + "/claims").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long claimAndGetId(String token, long itemId) throws Exception {
        String body = claim(token, itemId, "Three").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions postAs(String token, String url) throws Exception {
        return mockMvc.perform(post(url).header("Authorization", "Bearer " + token));
    }

    private ResultActions getAs(String token, String url) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + token));
    }

    @Test
    void claimHidesContactUntilApproved() throws Exception {
        String poster = newUser();
        String claimant = newUser();
        long itemId = postItem(poster);

        claim(claimant, itemId, "Three")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.answer").value("Three"))
                .andExpect(jsonPath("$.contactEmail").doesNotExist());
    }

    @Test
    void cannotClaimOwnItem() throws Exception {
        String poster = newUser();
        long itemId = postItem(poster);

        claim(poster, itemId, "Three").andExpect(status().isForbidden());
    }

    @Test
    void verificationAnswerIsRequired() throws Exception {
        String poster = newUser();
        long itemId = postItem(poster);

        claim(newUser(), itemId, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Please answer the verification question"));
    }

    @Test
    void cannotClaimTwice() throws Exception {
        String poster = newUser();
        String claimant = newUser();
        long itemId = postItem(poster);

        claim(claimant, itemId, "Three").andExpect(status().isCreated());
        claim(claimant, itemId, "Three").andExpect(status().isConflict());
    }

    @Test
    void onlyPosterCanSeeAndDecideClaims() throws Exception {
        String poster = newUser();
        String claimant = newUser();
        long itemId = postItem(poster);
        long claimId = claimAndGetId(claimant, itemId);

        getAs(claimant, "/api/items/" + itemId + "/claims").andExpect(status().isForbidden());
        postAs(claimant, "/api/claims/" + claimId + "/approve").andExpect(status().isForbidden());
        getAs(poster, "/api/items/" + itemId + "/claims")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void fullFlowApproveThenReturn() throws Exception {
        String poster = newUser();
        String winner = newUser();
        String other = newUser();
        long itemId = postItem(poster);
        long winnerClaim = claimAndGetId(winner, itemId);
        long otherClaim = claimAndGetId(other, itemId);

        // The poster was notified twice.
        getAs(poster, "/api/notifications")
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.notifications[0].type").value("CLAIM_RECEIVED"));

        // Approve one claim: the item is claimed and the other claim is declined automatically.
        postAs(poster, "/api/claims/" + winnerClaim + "/approve")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.contactEmail").exists());
        mockMvc.perform(get("/api/items/" + itemId)).andExpect(jsonPath("$.status").value("CLAIMED"));
        postAs(poster, "/api/claims/" + otherClaim + "/approve").andExpect(status().isConflict());

        // The winner now sees the poster's email; the other claimant sees a rejection.
        getAs(winner, "/api/me/claims")
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[0].contactEmail").exists());
        getAs(other, "/api/me/claims").andExpect(jsonPath("$[0].status").value("REJECTED"));
        getAs(other, "/api/notifications").andExpect(jsonPath("$.notifications[0].type").value("CLAIM_REJECTED"));

        // New claims are refused once the item is claimed.
        claim(newUser(), itemId, "Three").andExpect(status().isConflict());

        // Mark as returned.
        postAs(poster, "/api/items/" + itemId + "/returned")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"));
        getAs(winner, "/api/notifications").andExpect(jsonPath("$.notifications[0].type").value("ITEM_RETURNED"));
        postAs(poster, "/api/items/" + itemId + "/returned").andExpect(status().isConflict());
    }

    @Test
    void rejectKeepsItemOpen() throws Exception {
        String poster = newUser();
        long itemId = postItem(poster);
        long claimId = claimAndGetId(newUser(), itemId);

        postAs(poster, "/api/claims/" + claimId + "/reject")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(get("/api/items/" + itemId)).andExpect(jsonPath("$.status").value("OPEN"));
        postAs(poster, "/api/items/" + itemId + "/returned").andExpect(status().isConflict());
    }

    @Test
    void notificationsCanBeMarkedRead() throws Exception {
        String poster = newUser();
        long itemId = postItem(poster);
        claimAndGetId(newUser(), itemId);

        String body = getAs(poster, "/api/notifications").andReturn().getResponse().getContentAsString();
        long notificationId = ((Number) JsonPath.read(body, "$.notifications[0].id")).longValue();

        postAs(newUser(), "/api/notifications/" + notificationId + "/read").andExpect(status().isNotFound());
        postAs(poster, "/api/notifications/" + notificationId + "/read").andExpect(status().isNoContent());
        getAs(poster, "/api/notifications").andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void deletingAnItemRemovesItsClaims() throws Exception {
        String poster = newUser();
        String claimant = newUser();
        long itemId = postItem(poster);
        claimAndGetId(claimant, itemId);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/items/" + itemId).header("Authorization", "Bearer " + poster))
                .andExpect(status().isNoContent());
        getAs(claimant, "/api/me/claims").andExpect(jsonPath("$.length()").value(0));
    }
}
