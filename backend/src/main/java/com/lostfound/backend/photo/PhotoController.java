package com.lostfound.backend.photo;

import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class PhotoController {

    private static final Pattern FILE_NAME = Pattern.compile("[0-9a-f-]{36}(_thumb)?\\.jpg");

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    /** Owner only. Send as multipart/form-data with the image in a field called "file". */
    @PostMapping(path = "/api/items/{itemId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoResponse upload(@AuthenticationPrincipal Jwt jwt, @PathVariable Long itemId,
                                @RequestParam("file") MultipartFile file) {
        return photoService.add(Long.valueOf(jwt.getSubject()), itemId, file);
    }

    @DeleteMapping("/api/items/{itemId}/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long itemId, @PathVariable Long photoId) {
        photoService.delete(Long.valueOf(jwt.getSubject()), itemId, photoId);
    }

    /** Public. A photo's file never changes (a new upload gets a new name), so browsers may cache it forever. */
    @GetMapping("/api/photos/{fileName:.+}")
    public ResponseEntity<byte[]> get(@PathVariable String fileName) {
        if (!FILE_NAME.matcher(fileName).matches()) {
            return ResponseEntity.notFound().build();
        }
        return photoService.load(fileName)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
