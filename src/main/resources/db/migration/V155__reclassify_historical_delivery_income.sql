-- Preserve the original sale journals and add balanced reclassification entries.
INSERT INTO journal_entries (description, entry_date, reference_no, staff_id, status)
SELECT CONCAT('Reclassify delivery income - ', s.sale_code),
       s.sale_date,
       CONCAT('DELIVERY-RECLASS-', s.id),
       s.staff_id,
       'POSTED'
FROM sales s
WHERE s.delivery_charge > 0
  AND COALESCE(s.is_voided, 0) = 0
  AND EXISTS (
      SELECT 1 FROM journal_entries original_entry
      WHERE original_entry.reference_no = s.sale_code
        AND COALESCE(original_entry.status, 'POSTED') = 'POSTED'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM journal_entries original_entry
      JOIN journal_details original_detail ON original_detail.journal_id = original_entry.id
      JOIN chart_of_accounts original_account ON original_account.id = original_detail.account_id
      WHERE original_entry.reference_no = s.sale_code
        AND COALESCE(original_entry.status, 'POSTED') = 'POSTED'
        AND original_account.code = 'INC-012'
        AND original_detail.credit > 0
  )
  AND NOT EXISTS (
      SELECT 1 FROM journal_entries existing
      WHERE existing.reference_no = CONCAT('DELIVERY-RECLASS-', s.id)
  );

INSERT INTO journal_details (journal_id, account_id, debit, credit)
SELECT reclass.id, sales_account.id, s.delivery_charge, 0
FROM sales s
JOIN journal_entries reclass
  ON reclass.reference_no = CONCAT('DELIVERY-RECLASS-', s.id)
JOIN chart_of_accounts sales_account ON sales_account.code = 'INC-002'
WHERE s.delivery_charge > 0
  AND NOT EXISTS (
      SELECT 1 FROM journal_details existing
      WHERE existing.journal_id = reclass.id
  );

INSERT INTO journal_details (journal_id, account_id, debit, credit)
SELECT reclass.id, delivery_account.id, 0, s.delivery_charge
FROM sales s
JOIN journal_entries reclass
  ON reclass.reference_no = CONCAT('DELIVERY-RECLASS-', s.id)
JOIN chart_of_accounts delivery_account ON delivery_account.code = 'INC-012'
WHERE s.delivery_charge > 0
  AND EXISTS (
      SELECT 1 FROM journal_details sales_line
      JOIN chart_of_accounts account ON account.id = sales_line.account_id
      WHERE sales_line.journal_id = reclass.id
        AND account.code = 'INC-002'
  )
  AND NOT EXISTS (
      SELECT 1 FROM journal_details existing
      JOIN chart_of_accounts account ON account.id = existing.account_id
      WHERE existing.journal_id = reclass.id
        AND account.code = 'INC-012'
  );

-- Balance updates group a precomputed year alias so ONLY_FULL_GROUP_BY cannot
-- treat entry.entry_date as a non-aggregated SELECT expression.
UPDATE account_balances balance
JOIN chart_of_accounts account ON account.id = balance.account_id
JOIN (
    SELECT year_rows.fiscal_year_num, COALESCE(SUM(year_rows.line_amount), 0) AS amount
    FROM (
        SELECT YEAR(entry.entry_date) AS fiscal_year_num, detail.debit AS line_amount
        FROM journal_entries entry
        JOIN journal_details detail ON detail.journal_id = entry.id
        JOIN chart_of_accounts detail_account ON detail_account.id = detail.account_id
        WHERE entry.reference_no LIKE 'DELIVERY-RECLASS-%'
          AND COALESCE(entry.status, 'POSTED') = 'POSTED'
          AND detail_account.code = 'INC-002'
    ) year_rows
    GROUP BY year_rows.fiscal_year_num
) reclass_amount ON reclass_amount.amount > 0
                  AND CAST(balance.fiscal_year AS UNSIGNED) = reclass_amount.fiscal_year_num
SET balance.current_balance = COALESCE(balance.current_balance, 0) - reclass_amount.amount,
    balance.last_updated = CURRENT_TIMESTAMP(6)
WHERE account.code = 'INC-002'
;

INSERT INTO account_balances (account_id, fiscal_year, opening_balance, current_balance, last_updated)
SELECT account.id,
       CAST(reclass_amount.fiscal_year_num AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci,
       0, -reclass_amount.amount, CURRENT_TIMESTAMP(6)
FROM chart_of_accounts account
JOIN (
    SELECT year_rows.fiscal_year_num, COALESCE(SUM(year_rows.line_amount), 0) AS amount
    FROM (
        SELECT YEAR(entry.entry_date) AS fiscal_year_num, detail.debit AS line_amount
        FROM journal_entries entry
        JOIN journal_details detail ON detail.journal_id = entry.id
        JOIN chart_of_accounts detail_account ON detail_account.id = detail.account_id
        WHERE entry.reference_no LIKE 'DELIVERY-RECLASS-%'
          AND COALESCE(entry.status, 'POSTED') = 'POSTED'
          AND detail_account.code = 'INC-002'
    ) year_rows
    GROUP BY year_rows.fiscal_year_num
) reclass_amount ON reclass_amount.amount > 0
WHERE account.code = 'INC-002'
  AND NOT EXISTS (
      SELECT 1 FROM account_balances existing
      WHERE existing.account_id = account.id
        AND CAST(existing.fiscal_year AS UNSIGNED) = reclass_amount.fiscal_year_num
  );

UPDATE account_balances balance
JOIN chart_of_accounts account ON account.id = balance.account_id
JOIN (
    SELECT year_rows.fiscal_year_num, COALESCE(SUM(year_rows.line_amount), 0) AS amount
    FROM (
        SELECT YEAR(entry.entry_date) AS fiscal_year_num, detail.credit AS line_amount
        FROM journal_entries entry
        JOIN journal_details detail ON detail.journal_id = entry.id
        JOIN chart_of_accounts detail_account ON detail_account.id = detail.account_id
        WHERE entry.reference_no LIKE 'DELIVERY-RECLASS-%'
          AND COALESCE(entry.status, 'POSTED') = 'POSTED'
          AND detail_account.code = 'INC-012'
    ) year_rows
    GROUP BY year_rows.fiscal_year_num
) reclass_amount ON reclass_amount.amount > 0
                  AND CAST(balance.fiscal_year AS UNSIGNED) = reclass_amount.fiscal_year_num
SET balance.current_balance = COALESCE(balance.current_balance, 0) + reclass_amount.amount,
    balance.last_updated = CURRENT_TIMESTAMP(6)
WHERE account.code = 'INC-012'
;

INSERT INTO account_balances (account_id, fiscal_year, opening_balance, current_balance, last_updated)
SELECT account.id,
       CAST(reclass_amount.fiscal_year_num AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci,
       0, reclass_amount.amount, CURRENT_TIMESTAMP(6)
FROM chart_of_accounts account
JOIN (
    SELECT year_rows.fiscal_year_num, COALESCE(SUM(year_rows.line_amount), 0) AS amount
    FROM (
        SELECT YEAR(entry.entry_date) AS fiscal_year_num, detail.credit AS line_amount
        FROM journal_entries entry
        JOIN journal_details detail ON detail.journal_id = entry.id
        JOIN chart_of_accounts detail_account ON detail_account.id = detail.account_id
        WHERE entry.reference_no LIKE 'DELIVERY-RECLASS-%'
          AND COALESCE(entry.status, 'POSTED') = 'POSTED'
          AND detail_account.code = 'INC-012'
    ) year_rows
    GROUP BY year_rows.fiscal_year_num
) reclass_amount ON reclass_amount.amount > 0
WHERE account.code = 'INC-012'
  AND NOT EXISTS (
      SELECT 1 FROM account_balances existing
      WHERE existing.account_id = account.id
        AND CAST(existing.fiscal_year AS UNSIGNED) = reclass_amount.fiscal_year_num
  );
