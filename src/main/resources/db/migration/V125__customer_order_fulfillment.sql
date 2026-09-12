-- Customer order fulfillment: delivery vs store pickup
ALTER TABLE customer_orders
    ADD COLUMN order_type VARCHAR(20) NULL,
    ADD COLUMN delivery_location_mode VARCHAR(20) NULL,
    ADD COLUMN delivery_address TEXT NULL,
    ADD COLUMN delivery_phone VARCHAR(40) NULL;
