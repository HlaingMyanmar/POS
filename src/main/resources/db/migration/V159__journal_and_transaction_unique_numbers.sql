UPDATE journal_entries
SET reference_no = NULL
WHERE reference_no IS NOT NULL AND TRIM(reference_no) = '';

UPDATE journal_entries je
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY reference_no ORDER BY id) AS rn
    FROM journal_entries
    WHERE reference_no IS NOT NULL
) ranked ON ranked.id = je.id
SET je.reference_no = CONCAT(je.reference_no, '-DUP-', je.id)
WHERE ranked.rn > 1;

ALTER TABLE journal_entries
    DROP INDEX idx_je_reference_no,
    ADD UNIQUE INDEX uk_journal_entries_reference_no (reference_no);

UPDATE payment_transactions
SET transaction_no = NULL
WHERE transaction_no IS NOT NULL AND TRIM(transaction_no) = '';

UPDATE payment_transactions pt
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY transaction_no ORDER BY id) AS rn
    FROM payment_transactions
    WHERE transaction_no IS NOT NULL
) ranked ON ranked.id = pt.id
SET pt.transaction_no = CONCAT(pt.transaction_no, '-DUP-', pt.id)
WHERE ranked.rn > 1;

ALTER TABLE payment_transactions
    ADD UNIQUE INDEX uk_payment_transactions_transaction_no (transaction_no);
