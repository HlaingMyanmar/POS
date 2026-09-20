INSERT INTO chart_of_accounts (account_name, account_type, code, parent_id)
SELECT 'Delivery Income', 'Income', 'INC-012', parent.id
FROM chart_of_accounts parent
WHERE parent.code = 'INC-001'
  AND NOT EXISTS (
      SELECT 1 FROM chart_of_accounts existing WHERE existing.code = 'INC-012'
  );
