package org.sspd.servicemgmt.bookingoptions.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

@Service
public class BookingPhotoStorageService {
    private static final int MAX_IMAGE_SIZE = 2048;
    private static final int THUMBNAIL_SIZE = 640;
    private final Path storageRoot;

    public BookingPhotoStorageService(@Value("${app.booking-photo.storage-dir}") String storageDir) {
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
    }

    public StoredPhoto store(String dataUrl, Integer bookingItemId, int slot) {
        try {
            Files.createDirectories(storageRoot);
            String encoded = dataUrl.substring(dataUrl.indexOf(',') + 1);
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
            if (source == null) throw new IllegalArgumentException("Unsupported device photo format");

            String directory = "booking-items/" + bookingItemId;
            Path folder = storageRoot.resolve(directory).normalize();
            if (!folder.startsWith(storageRoot)) throw new IllegalArgumentException("Invalid photo path");
            Files.createDirectories(folder);
            String name = UUID.randomUUID() + "-slot-" + slot;
            Path imageFile = folder.resolve(name + ".webp");
            Path thumbnailFile = folder.resolve(name + "-thumb.webp");
            writeWebp(resize(source, MAX_IMAGE_SIZE), imageFile);
            writeWebp(resize(source, THUMBNAIL_SIZE), thumbnailFile);
            return new StoredPhoto(
                    "/uploads/booking-photos/" + directory + "/" + imageFile.getFileName(),
                    "/uploads/booking-photos/" + directory + "/" + thumbnailFile.getFileName());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid device photo", ex);
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("Could not store device photo", ex);
        }
    }

    public void deleteExisting(String imagePath, String thumbnailPath) {
        deletePath(imagePath);
        deletePath(thumbnailPath);
    }

    private void deletePath(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) return;
        try {
            String relative = resourcePath.replace("/uploads/booking-photos/", "");
            Path resolved = storageRoot.resolve(relative).normalize();
            if (resolved.startsWith(storageRoot)) Files.deleteIfExists(resolved);
        } catch (IOException ignored) {
            // Cleanup must not prevent the business operation from completing.
        }
    }

    private static BufferedImage resize(BufferedImage source, int maxSize) {
        double scale = Math.min(1d, Math.min((double) maxSize / source.getWidth(), (double) maxSize / source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return result;
    }

    private static void writeWebp(BufferedImage image, Path target) throws IOException {
        if (!ImageIO.write(image, "webp", target.toFile())) {
            throw new IOException("WebP writer is unavailable");
        }
    }

    public record StoredPhoto(String imagePath, String thumbnailPath) { }
}
