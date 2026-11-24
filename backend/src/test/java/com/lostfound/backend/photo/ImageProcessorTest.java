package com.lostfound.backend.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageProcessorTest {

    static byte[] image(String format, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x += 7) {
            for (int y = 0; y < height; y += 7) img.setRGB(x, y, 0x3366CC);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] jpeg) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    @Test
    void bigPhotoIsShrunkKeepingItsShape() throws IOException {
        ImageProcessor.Processed result = ImageProcessor.process(image("png", 3200, 2400));

        BufferedImage full = decode(result.full());
        BufferedImage thumb = decode(result.thumbnail());
        assertThat(full.getWidth()).isEqualTo(1600);
        assertThat(full.getHeight()).isEqualTo(1200);
        assertThat(thumb.getWidth()).isEqualTo(400);
        assertThat(thumb.getHeight()).isEqualTo(300);
        assertThat(ImageProcessor.isJpeg(result.full())).isTrue();
    }

    @Test
    void smallPhotoIsNotEnlarged() throws IOException {
        ImageProcessor.Processed result = ImageProcessor.process(image("jpg", 300, 200));

        assertThat(decode(result.full()).getWidth()).isEqualTo(300);
        assertThat(decode(result.thumbnail()).getWidth()).isEqualTo(300);
    }

    @Test
    void exifMetadataIsNotCopied() throws IOException {
        // A JPEG with an EXIF (APP1) block containing a fake GPS marker, inserted right after the start marker.
        byte[] plain = image("jpg", 400, 300);
        byte[] exifPayload = "Exif\0\0GPS-LATITUDE-25.0173".getBytes(StandardCharsets.ISO_8859_1);
        int length = exifPayload.length + 2;
        ByteArrayOutputStream withExif = new ByteArrayOutputStream();
        withExif.write(plain, 0, 2);                                             // FF D8
        withExif.write(new byte[] {(byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length});
        withExif.write(exifPayload);
        withExif.write(plain, 2, plain.length - 2);
        assertThat(new String(withExif.toByteArray(), StandardCharsets.ISO_8859_1)).contains("GPS-LATITUDE");

        ImageProcessor.Processed result = ImageProcessor.process(withExif.toByteArray());

        assertThat(new String(result.full(), StandardCharsets.ISO_8859_1)).doesNotContain("GPS-LATITUDE").doesNotContain("Exif");
        assertThat(new String(result.thumbnail(), StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
    }

    @Test
    void nonImagesAreRejectedEvenWithAnImageLikeStart() {
        assertThatThrownBy(() -> ImageProcessor.process("<script>alert(1)</script>".getBytes()))
                .isInstanceOf(ImageProcessor.InvalidImageException.class)
                .hasMessage("Only JPEG and PNG images are allowed");

        byte[] fakePng = new byte[64];
        fakePng[0] = (byte) 0x89; fakePng[1] = 'P'; fakePng[2] = 'N'; fakePng[3] = 'G';
        assertThatThrownBy(() -> ImageProcessor.process(fakePng))
                .isInstanceOf(ImageProcessor.InvalidImageException.class)
                .hasMessage("This file is not a readable image");
    }

    @Test
    void gifIsRejected() throws IOException {
        assertThatThrownBy(() -> ImageProcessor.process(image("gif", 50, 50)))
                .isInstanceOf(ImageProcessor.InvalidImageException.class);
    }

    @Test
    void hugeDimensionsAreRejectedBeforeDecoding() throws IOException {
        // 10,000 × 10,000 = 100 megapixels: a few hundred KB as PNG, but ~400 MB once decoded.
        byte[] bomb = image("png", 10_000, 10_000);
        assertThat(bomb.length).isLessThan(5 * 1024 * 1024);

        assertThatThrownBy(() -> ImageProcessor.process(bomb))
                .isInstanceOf(ImageProcessor.InvalidImageException.class)
                .hasMessage("Image dimensions are too large");
    }

    @Test
    void transparentPngBecomesWhiteNotBlack() throws IOException {
        BufferedImage transparent = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB); // all transparent
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(transparent, "png", out);

        BufferedImage result = decode(ImageProcessor.process(out.toByteArray()).full());

        int rgb = result.getRGB(50, 50) & 0xFFFFFF;
        assertThat((rgb >> 16) & 0xFF).isGreaterThan(240);
        assertThat(rgb & 0xFF).isGreaterThan(240);
    }
}
