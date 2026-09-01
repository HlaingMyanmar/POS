package org.sspd.servicemgmt.stockoptions.productoptions.service;

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
public class ProductPhotoStorageService {
    private static final int MAX_IMAGE_SIZE = 1600;
    private static final int THUMBNAIL_SIZE = 320;
    private final Path storageRoot;

    public ProductPhotoStorageService(@Value("${app.product-photo.storage-dir}") String storageDir) {
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
    }

    public StoredPhoto store(String dataUrl, Integer productId) {
        try {
            Files.createDirectories(storageRoot);
            if (dataUrl == null || dataUrl.isBlank()) {
                throw new IllegalArgumentException("Product photo payload is required");
            }
            int commaIndex = dataUrl.indexOf(',');
            if (commaIndex < 0) {
                throw new IllegalArgumentException("Unsupported product photo format");
            }
            String encoded = dataUrl.substring(commaIndex + 1);
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
            if (source == null) {
                throw new IllegalArgumentException("Unsupported product photo format");
            }

            String directory = "products/" + productId;
            Path folder = storageRoot.resolve(directory).normalize();
            if (!folder.startsWith(storageRoot)) {
                throw new IllegalArgumentException("Invalid product photo path");
            }
            Files.createDirectories(folder);

            String name = UUID.randomUUID().toString();
            Path imageFile = folder.resolve(name + ".webp");
            Path thumbnailFile = folder.resolve(name + "-thumb.webp");
            writeWebp(resize(source, MAX_IMAGE_SIZE), imageFile);
            writeWebp(resize(source, THUMBNAIL_SIZE), thumbnailFile);

            return new StoredPhoto(
                    "/uploads/product-photos/" + directory + "/" + imageFile.getFileName(),
                    "/uploads/product-photos/" + directory + "/" + thumbnailFile.getFileName(),
                    source.getWidth(),
                    source.getHeight()
            );
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid product photo", ex);
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("Could not store product photo", ex);
        }
    }

    public void deleteExisting(String imagePath, String thumbnailPath) {
        if (imagePath == null && thumbnailPath == null) return;
        try {
            if (imagePath != null) {
                Path resolved = storageRoot.resolve(imagePath.replace("/uploads/product-photos/", "")).normalize();
                if (resolved.startsWith(storageRoot)) Files.deleteIfExists(resolved);
            }
            if (thumbnailPath != null) {
                Path resolved = storageRoot.resolve(thumbnailPath.replace("/uploads/product-photos/", "")).normalize();
                if (resolved.startsWith(storageRoot)) Files.deleteIfExists(resolved);
            }
        } catch (IOException ignored) {
            // intentionally ignore cleanup failures during replacement
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

    public record StoredPhoto(String imagePath, String thumbnailPath, int width, int height) { }
}
