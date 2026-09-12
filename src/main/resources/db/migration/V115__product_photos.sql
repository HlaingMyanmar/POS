-- Multi-slot product photos (up to 3), same pattern as booking item photos.
CREATE TABLE product_photos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    product_id INT NOT NULL,
    slot INT NOT NULL,
    file_name VARCHAR(255) NULL,
    content_type VARCHAR(120) NULL,
    data_url LONGTEXT NULL,
    image_path VARCHAR(500) NULL,
    thumbnail_path VARCHAR(500) NULL,
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_photo_slot UNIQUE (product_id, slot),
    CONSTRAINT fk_product_photos_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

-- Migrate existing single product photo into slot 1.
INSERT INTO product_photos (product_id, slot, image_path, thumbnail_path, content_type, uploaded_at)
SELECT id, 1, image_path, thumbnail_path, COALESCE(image_mime_type, 'image/jpeg'), NOW()
FROM products
WHERE image_path IS NOT NULL
  AND thumbnail_path IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM product_photos pp WHERE pp.product_id = products.id AND pp.slot = 1
  );
