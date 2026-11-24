package com.lostfound.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Registers a user and logs in, returning an access token for use in tests. */
public final class AuthTestHelper {

    private AuthTestHelper() {
    }

    public static String registerAndLogin(MockMvc mockMvc, String email) throws Exception {
        String register = """
                {"email":"%s","password":"password123","displayName":"Tester"}
                """.formatted(email);
        String login = """
                {"email":"%s","password":"password123"}
                """.formatted(email);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(register))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(response, "$.accessToken");
    }
}
