ALTER TABLE customer_orders
    ADD COLUMN collection_proof_id INT NULL,
    ADD COLUMN collection_reference VARCHAR(120) NULL,
    ADD KEY idx_customer_order_collection_proof (collection_proof_id),
    ADD CONSTRAINT fk_customer_order_collection_proof
        FOREIGN KEY (collection_proof_id) REFERENCES customer_order_payment_proofs (id);
