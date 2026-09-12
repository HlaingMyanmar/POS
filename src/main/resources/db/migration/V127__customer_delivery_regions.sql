-- Delivery regions (ရန်ကုန်တိုင်း / ပြည်နယ်) + link townships
CREATE TABLE customer_delivery_regions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_customer_delivery_region_name (name)
);

INSERT INTO customer_delivery_regions (name, kind, active, sort_order, created_at, updated_at) VALUES
('ရန်ကုန်တိုင်း', 'YANGON', 1, 1, NOW(6), NOW(6)),
('မန္တလေးတိုင်း', 'STATE', 1, 2, NOW(6), NOW(6)),
('ပဲခူးတိုင်း', 'STATE', 1, 3, NOW(6), NOW(6)),
('စစ်ကိုင်းတိုင်း', 'STATE', 1, 4, NOW(6), NOW(6)),
('မကွေးတိုင်း', 'STATE', 1, 5, NOW(6), NOW(6)),
('တနင်္သာရီတိုင်း', 'STATE', 1, 6, NOW(6), NOW(6)),
('ဧရာဝတီတိုင်း', 'STATE', 1, 7, NOW(6), NOW(6)),
('ရှမ်းပြည်နယ်', 'STATE', 1, 8, NOW(6), NOW(6)),
('ကချင်ပြည်နယ်', 'STATE', 1, 9, NOW(6), NOW(6)),
('ကယားပြည်နယ်', 'STATE', 1, 10, NOW(6), NOW(6)),
('ကရင်ပြည်နယ်', 'STATE', 1, 11, NOW(6), NOW(6)),
('ချင်းပြည်နယ်', 'STATE', 1, 12, NOW(6), NOW(6)),
('မွန်ပြည်နယ်', 'STATE', 1, 13, NOW(6), NOW(6)),
('ရခိုင်ပြည်နယ်', 'STATE', 1, 14, NOW(6), NOW(6));

ALTER TABLE customer_delivery_townships
    ADD COLUMN region_id INT NULL AFTER id;

UPDATE customer_delivery_townships t
    JOIN customer_delivery_regions r ON r.name = 'ရန်ကုန်တိုင်း'
SET t.region_id = r.id
WHERE t.region_id IS NULL;

ALTER TABLE customer_delivery_townships
    MODIFY COLUMN region_id INT NOT NULL,
    DROP INDEX uk_customer_delivery_township_name,
    ADD UNIQUE KEY uk_customer_delivery_township_region_name (region_id, name),
    ADD CONSTRAINT fk_customer_delivery_township_region
        FOREIGN KEY (region_id) REFERENCES customer_delivery_regions (id);

-- Extra Yangon townships (ignore if name already exists under Yangon)
INSERT INTO customer_delivery_townships (region_id, name, delivery_charge, active, sort_order, created_at, updated_at)
SELECT r.id, v.name, v.charge, 1, v.sort_order, NOW(6), NOW(6)
FROM customer_delivery_regions r
JOIN (
    SELECT 'ဒဂုံမြို့သစ် (ဆိပ်ကမ်း)' AS name, 3500.00 AS charge, 10 AS sort_order UNION ALL
    SELECT 'ဒဂုံမြို့သစ် (အရှေ့)', 3500.00, 11 UNION ALL
    SELECT 'ဒဂုံမြို့သစ် (မြောက်)', 3500.00, 12 UNION ALL
    SELECT 'လှိုင်သာယာ', 3000.00, 13 UNION ALL
    SELECT 'အင်းစိန်', 3000.00, 14 UNION ALL
    SELECT 'မင်္ဂလာဒုံ', 3500.00, 15 UNION ALL
    SELECT 'သန်လျင်', 4000.00, 16 UNION ALL
    SELECT 'ကျောက်တန်း', 4500.00, 17 UNION ALL
    SELECT 'တွံတေး', 4500.00, 18 UNION ALL
    SELECT 'ကိုကိုးကျွန်း', 0.00, 19
) v
WHERE r.name = 'ရန်ကုန်တိုင်း'
  AND NOT EXISTS (
      SELECT 1 FROM customer_delivery_townships x
      WHERE x.region_id = r.id AND x.name = v.name
  );

-- Sample state townships (charges editable later)
INSERT INTO customer_delivery_townships (region_id, name, delivery_charge, active, sort_order, created_at, updated_at)
SELECT r.id, v.name, v.charge, 1, v.sort_order, NOW(6), NOW(6)
FROM customer_delivery_regions r
JOIN (
    SELECT 'မန္တလေးတိုင်း' AS region_name, 'ချမ်းမြသာစည်' AS name, 8000.00 AS charge, 1 AS sort_order UNION ALL
    SELECT 'မန္တလေးတိုင်း', 'မဟာအောင်မြေ', 8000.00, 2 UNION ALL
    SELECT 'မန္တလေးတိုင်း', 'အောင်မြေသာစံ', 8000.00, 3 UNION ALL
    SELECT 'ပဲခူးတိုင်း', 'ပဲခူး', 6000.00, 1 UNION ALL
    SELECT 'ပဲခူးတိုင်း', 'တောင်ငူ', 7000.00, 2 UNION ALL
    SELECT 'ဧရာဝတီတိုင်း', 'ပုသိမ်', 7000.00, 1 UNION ALL
    SELECT 'ဧရာဝတီတိုင်း', 'မြောင်းမြ', 7500.00, 2 UNION ALL
    SELECT 'ရှမ်းပြည်နယ်', 'တောင်ကြီး', 12000.00, 1 UNION ALL
    SELECT 'ရှမ်းပြည်နယ်', 'လားရှိုး', 15000.00, 2 UNION ALL
    SELECT 'မွန်ပြည်နယ်', 'မော်လမြိုင်', 9000.00, 1 UNION ALL
    SELECT 'ကရင်ပြည်နယ်', 'ဘားအံ', 10000.00, 1
) v ON v.region_name = r.name
WHERE NOT EXISTS (
    SELECT 1 FROM customer_delivery_townships x
    WHERE x.region_id = r.id AND x.name = v.name
);
