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
    private static final int MAX_IMAGE_SIZE = 2048;
    private static final int THUMBNAIL_SIZE = 640;
    private final Path storageRoot;

    public ProductPhotoStorageService(@Value("${app.product-photo.storage-dir}") String storageDir) {
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
    }

    public StoredPhoto store(String dataUrl, Integer productId) {
        return store(dataUrl, productId, 1);
    }

    public StoredPhoto store(String dataUrl, Integer productId, int slot) {
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

            String name = "slot-" + slot + "-" + UUID.randomUUID();
            Path imageFile = folder.resolve(name + ".jpg");
            Path thumbnailFile = folder.resolve(name + "-thumb.jpg");
            writeJpeg(resize(source, MAX_IMAGE_SIZE), imageFile, 0.92f);
            writeJpeg(resize(source, THUMBNAIL_SIZE), thumbnailFile, 0.88f);

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

    private static void writeJpeg(BufferedImage image, Path target, float quality) throws IOException {
        var writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("JPEG writer is unavailable");
        }
        var writer = writers.next();
        try (var out = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(out);
            var params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(quality);
            }
            writer.write(null, new javax.imageio.IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
    }

    public record StoredPhoto(String imagePath, String thumbnailPath, int width, int height) { }
}
