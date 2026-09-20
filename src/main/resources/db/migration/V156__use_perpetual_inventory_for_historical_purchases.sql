-- Convert legacy periodic purchase/return journals to the perpetual inventory
-- method used by sales (Inventory -> COGS). Original journals remain intact.
-- Correction journals are never treated as sources, and stored balances are
-- restated from opening + all journals so a partial rerun cannot double-apply.

INSERT INTO journal_entries (description, entry_date, reference_no, staff_id, status)
SELECT CONCAT('Reclassify purchase to inventory - ', source.reference_no),
       source.entry_date, CONCAT('PERPETUAL-PURCHASE-', source.id), source.staff_id, 'POSTED'
FROM journal_entries source
JOIN journal_details detail ON detail.journal_id = source.id
JOIN chart_of_accounts account ON account.id = detail.account_id AND account.code = 'EXP-007'
WHERE source.reference_no NOT LIKE 'PERPETUAL-PURCHASE-%'
  AND source.reference_no NOT LIKE 'PERPETUAL-RETURN-%'
  AND source.description NOT LIKE 'Reclassify purchase to inventory%'
  AND source.description NOT LIKE 'Reclassify purchase return to inventory%'
GROUP BY source.id, source.reference_no, source.entry_date, source.staff_id
HAVING SUM(detail.debit) <> SUM(detail.credit)
   AND NOT EXISTS (SELECT 1 FROM journal_entries existing
                   WHERE existing.reference_no = CONCAT('PERPETUAL-PURCHASE-', source.id));

INSERT INTO journal_details (journal_id, account_id, debit, credit)
SELECT correction.id, inventory.id,
       GREATEST(SUM(detail.debit) - SUM(detail.credit), 0),
       GREATEST(SUM(detail.credit) - SUM(detail.debit), 0)
FROM journal_entries source
JOIN journal_details detail ON detail.journal_id = source.id
JOIN chart_of_accounts legacy ON legacy.id = detail.account_id AND legacy.code = 'EXP-007'
JOIN journal_entries correction ON correction.reference_no = CONCAT('PERPETUAL-PURCHASE-', source.id)
JOIN chart_of_accounts inventory ON inventory.code = 'ASS-005'
WHERE source.reference_no NOT LIKE 'PERPETUAL-PURCHASE-%'
  AND source.reference_no NOT LIKE 'PERPETUAL-RETURN-%'
  AND NOT EXISTS (SELECT 1 FROM journal_details existing WHERE existing.journal_id = correction.id)
GROUP BY correction.id, inventory.id;

INSERT INTO journal_details (journal_id, account_id, debit, credit)
SELECT correction.id, purchases.id,
       GREATEST(SUM(detail.credit) - SUM(detail.debit), 0),
       GREATEST(SUM(detail.debit) - SUM(detail.credit), 0)
FROM journal_entries source
JOIN journal_details detail ON detail.journal_id = source.id
JOIN chart_of_accounts legacy ON legacy.id = detail.account_id AND legacy.code = 'EXP-007'
JOIN journal_entries correction ON correction.reference_no = CONCAT('PERPETUAL-PURCHASE-', source.id)
JOIN chart_of_accounts purchases ON purchases.code = 'EXP-007'
WHERE source.reference_no NOT LIKE 'PERPETUAL-PURCHASE-%'
  AND source.reference_no NOT LIKE 'PERPETUAL-RETURN-%'
  AND EXISTS (SELECT 1 FROM journal_details present WHERE present.journal_id = correction.id)
  AND NOT EXISTS (SELECT 1 FROM journal_details existing
                  WHERE existing.journal_id = correction.id AND existing.account_id = purchases.id)
GROUP BY correction.id, purchases.id;

INSERT INTO journal_entries (description, entry_date, reference_no, staff_id, status)
SELECT CONCAT('Reclassify purchase return to inventory - ', source.reference_no),
       source.entry_date, CONCAT('PERPETUAL-RETURN-', source.id), source.staff_id, 'POSTED'
FROM journal_entries source
JOIN journal_details detail ON detail.journal_id = source.id
JOIN chart_of_accounts account ON account.id = detail.account_id AND account.code = 'INC-007'
WHERE source.reference_no NOT LIKE 'PERPETUAL-PURCHASE-%'
  AND source.reference_no NOT LIKE 'PERPETUAL-RETURN-%'
  AND source.description NOT LIKE 'Reclassify purchase to inventory%'
  AND source.description NOT LIKE 'Reclassify purchase return to inventory%'
GROUP BY source.id, source.reference_no, source.entry_date, source.staff_id
HAVING SUM(detail.debit) <> SUM(detail.credit)
   AND NOT EXISTS (SELECT 1 FROM journal_entries existing
                   WHERE existing.reference_no = CONCAT('PERPETUAL-RETURN-', source.id));

INSERT INTO journal_details (journal_id, account_id, debit, credit)
SELECT correction.id, returns_account.id,
       GREATEST(SUM(detail.credit) - SUM(detail.debit), 0),
       GREATEST(SUM(detail.debit) - SUM(detail.credit), 0)
FROM journal_entries source
JOIN journal_details detail ON detail.journal_id = source.id
JOIN chart_of_accounts legacy ON legacy.id = detail.account_id AND legacy.code = 'INC-007'
JOIN journal_entries correction ON correction.reference_no = CONCAT('PERPETUAL-RETURN-', source.id)
JOIN chart_of_accounts returns_account ON returns_account.code = 'INC-007'
WHERE source.reference_no NOT LIKE 'PERPETUAL-PURCHASE-%'
  AND source.reference_no NOT LIKE 'PERPETUAL-RETURN-%'
  AND NOT EXISTS (SELECT 1 FROM journal_details existing WHERE existing.journal_id = correction.id)
GROUP BY correction.id, returns_account.id;

INSERT INTO journal_details (journal_id, account_id, debit, credit)
SELECT correction.id, inventory.id,
       GREATEST(SUM(detail.debit) - SUM(detail.credit), 0),
       GREATEST(SUM(detail.credit) - SUM(detail.debit), 0)
FROM journal_entries source
JOIN journal_details detail ON detail.journal_id = source.id
JOIN chart_of_accounts legacy ON legacy.id = detail.account_id AND legacy.code = 'INC-007'
JOIN journal_entries correction ON correction.reference_no = CONCAT('PERPETUAL-RETURN-', source.id)
JOIN chart_of_accounts inventory ON inventory.code = 'ASS-005'
WHERE source.reference_no NOT LIKE 'PERPETUAL-PURCHASE-%'
  AND source.reference_no NOT LIKE 'PERPETUAL-RETURN-%'
  AND EXISTS (SELECT 1 FROM journal_details present WHERE present.journal_id = correction.id)
  AND NOT EXISTS (SELECT 1 FROM journal_details existing
                  WHERE existing.journal_id = correction.id AND existing.account_id = inventory.id)
GROUP BY correction.id, inventory.id;

-- Restate stored balances from opening + every journal in that fiscal year.
-- Incremental +delta is not used, so a partial rerun cannot apply twice.
UPDATE account_balances balance
JOIN (
    SELECT year_rows.account_id, year_rows.fiscal_year_num, SUM(year_rows.line_net) journal_net
    FROM (
        SELECT detail.account_id,
               YEAR(entry.entry_date) fiscal_year_num,
               CASE WHEN account.account_type IN ('Asset', 'Expense')
                    THEN detail.debit - detail.credit ELSE detail.credit - detail.debit END line_net
        FROM journal_entries entry
        JOIN journal_details detail ON detail.journal_id = entry.id
        JOIN chart_of_accounts account ON account.id = detail.account_id
        WHERE detail.account_id IN (
            SELECT affected.account_id
            FROM journal_entries correction
            JOIN journal_details affected ON affected.journal_id = correction.id
            WHERE correction.reference_no LIKE 'PERPETUAL-PURCHASE-%'
               OR correction.reference_no LIKE 'PERPETUAL-RETURN-%'
        )
    ) year_rows
    GROUP BY year_rows.account_id, year_rows.fiscal_year_num
) restated ON restated.account_id = balance.account_id
          AND CAST(balance.fiscal_year AS UNSIGNED) = restated.fiscal_year_num
SET balance.current_balance = COALESCE(balance.opening_balance, 0) + restated.journal_net,
    balance.last_updated = CURRENT_TIMESTAMP(6);

INSERT INTO account_balances (account_id, fiscal_year, opening_balance, current_balance, last_updated)
SELECT restated.account_id,
       CAST(restated.fiscal_year_num AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci,
       0, restated.journal_net, CURRENT_TIMESTAMP(6)
FROM (
    SELECT year_rows.account_id, year_rows.fiscal_year_num, SUM(year_rows.line_net) journal_net
    FROM (
        SELECT detail.account_id,
               YEAR(entry.entry_date) fiscal_year_num,
               CASE WHEN account.account_type IN ('Asset', 'Expense')
                    THEN detail.debit - detail.credit ELSE detail.credit - detail.debit END line_net
        FROM journal_entries entry
        JOIN journal_details detail ON detail.journal_id = entry.id
        JOIN chart_of_accounts account ON account.id = detail.account_id
        WHERE detail.account_id IN (
            SELECT affected.account_id
            FROM journal_entries correction
            JOIN journal_details affected ON affected.journal_id = correction.id
            WHERE correction.reference_no LIKE 'PERPETUAL-PURCHASE-%'
               OR correction.reference_no LIKE 'PERPETUAL-RETURN-%'
        )
    ) year_rows
    GROUP BY year_rows.account_id, year_rows.fiscal_year_num
) restated
WHERE NOT EXISTS (
    SELECT 1 FROM account_balances existing
    WHERE existing.account_id = restated.account_id
      AND CAST(existing.fiscal_year AS UNSIGNED) = restated.fiscal_year_num
);
