package com.lostfound.backend.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.lostfound.backend.AuthTestHelper;
import com.lostfound.backend.TestcontainersConfiguration;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PhotoApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PhotoStorage storage;

    private String owner;
    private long itemId;

    @BeforeEach
    void createItem() throws Exception {
        owner = AuthTestHelper.registerAndLogin(mockMvc, "photo-" + UUID.randomUUID() + "@test.com");
        String body = mockMvc.perform(post("/api/items").header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"FOUND","title":"Photo test","categoryId":6,"locationText":"Campus","eventDate":"2026-11-20"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        itemId = ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions upload(String token, byte[] content) throws Exception {
        return mockMvc.perform(multipart("/api/items/" + itemId + "/photos")
                .file(new MockMultipartFile("file", "photo.png", "image/png", content))
                .header("Authorization", "Bearer " + token));
    }

    private String uploadAndGetUrl(String field) throws Exception {
        String body = upload(owner, ImageProcessorTest.image("png", 2000, 1000))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$." + field);
    }

    @Test
    void uploadedPhotoIsServedAsResizedJpegAndAppearsOnThePost() throws Exception {
        String url = uploadAndGetUrl("url");

        byte[] served = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(ImageIO.read(new ByteArrayInputStream(served)).getWidth()).isEqualTo(1600);

        mockMvc.perform(get("/api/items/" + itemId))
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.photos[0].url").value(url))
                .andExpect(jsonPath("$.photos[0].thumbnailUrl").value(url.replace(".jpg", "_thumb.jpg")));
    }

    @Test
    void onlyTheOwnerCanUpload() throws Exception {
        String stranger = AuthTestHelper.registerAndLogin(mockMvc, "photo-stranger-" + UUID.randomUUID() + "@test.com");

        upload(stranger, ImageProcessorTest.image("png", 100, 100)).andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/items/" + itemId + "/photos")
                        .file(new MockMultipartFile("file", "a.png", "image/png", ImageProcessorTest.image("png", 10, 10))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disguisedFilesAreRejected() throws Exception {
        upload(owner, "#!/bin/sh\nrm -rf /".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only JPEG and PNG images are allowed"));
    }

    @Test
    void atMostFivePhotosPerPost() throws Exception {
        for (int i = 0; i < 5; i++) {
            upload(owner, ImageProcessorTest.image("png", 50, 50)).andExpect(status().isCreated());
        }
        upload(owner, ImageProcessorTest.image("png", 50, 50))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A post can have at most 5 photos"));
    }

    @Test
    void tooLargeFileIsRejected() throws Exception {
        byte[] sixMegabytes = new byte[6 * 1024 * 1024];
        upload(owner, sixMegabytes).andExpect(status().isContentTooLarge());
    }

    @Test
    void deletingAPhotoRemovesItsFiles() throws Exception {
        String body = upload(owner, ImageProcessorTest.image("png", 100, 100))
                .andReturn().getResponse().getContentAsString();
        long photoId = ((Number) JsonPath.read(body, "$.id")).longValue();
        String fileName = ((String) JsonPath.read(body, "$.url")).replace("/api/photos/", "");
        assertThat(storage.load(fileName)).isPresent();

        mockMvc.perform(delete("/api/items/" + itemId + "/photos/" + photoId).header("Authorization", "Bearer " + owner))
                .andExpect(status().isNoContent());

        assertThat(storage.load(fileName)).isEmpty();
        assertThat(storage.load(fileName.replace(".jpg", "_thumb.jpg"))).isEmpty();
        mockMvc.perform(get("/api/items/" + itemId)).andExpect(jsonPath("$.photos.length()").value(0));
    }

    @Test
    void deletingThePostRemovesItsPhotoFiles() throws Exception {
        String fileName = uploadAndGetUrl("url").replace("/api/photos/", "");

        mockMvc.perform(delete("/api/items/" + itemId).header("Authorization", "Bearer " + owner))
                .andExpect(status().isNoContent());

        assertThat(storage.load(fileName)).isEmpty();
    }

    @Test
    void sneakyFileNamesAreNotServed() throws Exception {
        // Encoded "../" is refused even earlier, by Spring Security's request firewall (400).
        mockMvc.perform(get("/api/photos/..%2F..%2Fapplication.yml")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/photos/not-a-uuid.jpg")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/photos/" + UUID.randomUUID() + ".jpg")).andExpect(status().isNotFound());
    }
}
