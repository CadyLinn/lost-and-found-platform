package com.lostfound.backend.photo;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Turns an uploaded file into two clean JPEGs: a display size and a thumbnail.
 *
 * <ul>
 *   <li>Only real JPEG and PNG files are accepted, judged by their first bytes, not the file name.</li>
 *   <li>Width and height are read <em>before</em> decoding, so a tiny file that claims to be
 *       100,000 × 100,000 pixels (a "decompression bomb") is rejected without using any memory.</li>
 *   <li>The output is freshly encoded pixels only. Metadata such as EXIF GPS location is not copied.</li>
 * </ul>
 * Pure logic with no Spring, so it is easy to unit test.
 */
public final class ImageProcessor {

    public static final int FULL_SIZE = 1600;
    public static final int THUMB_SIZE = 400;
    static final long MAX_PIXELS = 40_000_000L; // e.g. 8000 × 5000
    private static final float JPEG_QUALITY = 0.85f;

    public record Processed(byte[] full, byte[] thumbnail) {
    }

    /** Thrown for anything that isn't an acceptable image. The message is safe to show to users. */
    public static class InvalidImageException extends RuntimeException {
        public InvalidImageException(String message) {
            super(message);
        }
    }

    private ImageProcessor() {
    }

    public static Processed process(byte[] upload) {
        if (!isJpeg(upload) && !isPng(upload)) {
            throw new InvalidImageException("Only JPEG and PNG images are allowed");
        }
        BufferedImage image = decodeSafely(upload);
        BufferedImage rgb = toRgb(image);
        return new Processed(
                encodeJpeg(scaleToFit(rgb, FULL_SIZE)),
                encodeJpeg(scaleToFit(rgb, THUMB_SIZE)));
    }

    static boolean isJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    static boolean isPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static BufferedImage decodeSafely(byte[] upload) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new InvalidImageException("This file is not a readable image");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true); // ignore metadata: we never use it
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width * height > MAX_PIXELS) {
                    throw new InvalidImageException("Image dimensions are too large");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidImageException invalid) throw invalid;
            throw new InvalidImageException("This file is not a readable image");
        }
    }

    /** JPEG has no transparency: draw onto a white background (transparent PNG areas become white). */
    private static BufferedImage toRgb(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    /** Shrinks so the longer side is at most {@code max} pixels. Never enlarges. */
    static BufferedImage scaleToFit(BufferedImage source, int max) {
        int w = source.getWidth();
        int h = source.getHeight();
        double scale = Math.min(1.0, (double) max / Math.max(w, h));
        if (scale == 1.0) return source;

        // Halve repeatedly first, then do the last step: much sharper than one big jump.
        BufferedImage current = source;
        int targetW = Math.max(1, (int) Math.round(w * scale));
        int targetH = Math.max(1, (int) Math.round(h * scale));
        while (current.getWidth() / 2 >= targetW && current.getHeight() / 2 >= targetH) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return resize(current, targetW, targetH);
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), params); // null metadata: nothing copied over
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode JPEG", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
