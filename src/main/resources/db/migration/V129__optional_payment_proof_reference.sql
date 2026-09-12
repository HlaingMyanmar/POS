-- Allow payment proofs without a bank transaction reference (screenshot-only).
ALTER TABLE customer_order_payment_proofs
    MODIFY COLUMN transaction_reference VARCHAR(120) NULL;
