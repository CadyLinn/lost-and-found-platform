package com.lostfound.backend.photo;

import java.io.Serializable;

/** A photo as sent to clients: URLs for the full image (max 1600 px) and a thumbnail (max 400 px). */
public record PhotoResponse(Long id, String url, String thumbnailUrl) implements Serializable {

    public static PhotoResponse from(ItemPhoto photo) {
        String base = "/api/photos/" + photo.getKey();
        return new PhotoResponse(photo.getId(), base + ".jpg", base + "_thumb.jpg");
    }
}
