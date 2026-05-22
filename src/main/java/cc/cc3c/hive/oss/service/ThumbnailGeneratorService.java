package cc.cc3c.hive.oss.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * Generates JPEG thumbnails from image files. Used for IMAGE_PREVIEW gallery.
 * Does not perform encryption; caller must upload with HiveOssTask.withEncryption.
 */
@Slf4j
@Service
public class ThumbnailGeneratorService {

    private static final int THUMB_MAX_WIDTH = 320;
    private static final int THUMB_MAX_HEIGHT = 320;
    private static final String THUMB_FORMAT = "jpg";

    private static final List<String> SUPPORTED_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif", "webp");

    /**
     * Result of thumbnail generation: JPEG bytes and original dimensions.
     */
    public record ThumbResult(byte[] thumbJpeg, int imageWidth, int imageHeight) {}

    /**
     * Returns true if the file name suggests an image type we can thumbnail.
     */
    public boolean isSupportedImage(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        int i = fileName.lastIndexOf('.');
        if (i < 0 || i >= fileName.length() - 1) {
            return false;
        }
        String ext = fileName.substring(i + 1).trim().toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.contains(ext);
    }

    /**
     * Generate thumbnail from image file. Returns empty if not an image or generation fails.
     */
    public java.util.Optional<ThumbResult> generateFromFile(File imageFile) {
        if (imageFile == null || !imageFile.isFile()) {
            return java.util.Optional.empty();
        }
        try {
            BufferedImage src = ImageIO.read(imageFile);
            if (src == null) {
                return java.util.Optional.empty();
            }
            return generateFromBufferedImage(src);
        } catch (IOException e) {
            log.warn("thumbnail generation failed for file {}", imageFile.getName(), e);
            return java.util.Optional.empty();
        }
    }

    /**
     * Generate thumbnail from image input stream. Caller is responsible for closing the stream.
     */
    public java.util.Optional<ThumbResult> generateFromStream(InputStream imageStream) {
        if (imageStream == null) {
            return java.util.Optional.empty();
        }
        try {
            BufferedImage src = ImageIO.read(imageStream);
            if (src == null) {
                return java.util.Optional.empty();
            }
            return generateFromBufferedImage(src);
        } catch (IOException e) {
            log.warn("thumbnail generation failed from stream", e);
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<ThumbResult> generateFromBufferedImage(BufferedImage src) throws IOException {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0) {
            return java.util.Optional.empty();
        }
        int thumbW = w;
        int thumbH = h;
        if (w > THUMB_MAX_WIDTH || h > THUMB_MAX_HEIGHT) {
            double scale = Math.min((double) THUMB_MAX_WIDTH / w, (double) THUMB_MAX_HEIGHT / h);
            thumbW = Math.max(1, (int) Math.round(w * scale));
            thumbH = Math.max(1, (int) Math.round(h * scale));
        }
        BufferedImage thumb = new BufferedImage(thumbW, thumbH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, thumbW, thumbH, null);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(thumb, THUMB_FORMAT, out)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new ThumbResult(out.toByteArray(), w, h));
    }
}
