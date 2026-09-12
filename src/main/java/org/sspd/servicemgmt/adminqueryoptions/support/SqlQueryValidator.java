package org.sspd.servicemgmt.adminqueryoptions.support;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class SqlQueryValidator {

    public enum Mode { READ, WRITE }

    private static final int MAX_SQL_LENGTH = 10_000;

    private static final Set<String> ALWAYS_FORBIDDEN = Set.of(
            "DROP", "TRUNCATE", "ALTER", "CREATE", "GRANT", "REVOKE", "RENAME",
            "LOAD", "OUTFILE", "INFILE", "LOAD_FILE", "SHUTDOWN", "KILL",
            "EXEC", "EXECUTE", "CALL", "PREPARE", "DEALLOCATE", "HANDLER",
            "LOCK", "UNLOCK", "OPTIMIZE", "REPAIR", "ANALYZE", "BACKUP", "RESTORE"
    );

    private static final Set<String> READ_FORBIDDEN = Set.of(
            "INSERT", "UPDATE", "DELETE", "REPLACE", "MERGE", "UPSERT"
    );

    private SqlQueryValidator() {}

    public static Mode parseMode(String raw) {
        if (raw == null || raw.isBlank()) return Mode.READ;
        try {
            return Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("mode must be READ or WRITE");
        }
    }

    public static String normalize(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL is required");
        }
        String trimmed = sql.trim();
        if (trimmed.length() > MAX_SQL_LENGTH) {
            throw new IllegalArgumentException("SQL exceeds maximum length (" + MAX_SQL_LENGTH + " chars)");
        }
        String withoutTrailing = trimmed.replaceAll(";\\s*$", "");
        if (withoutTrailing.contains(";")) {
            throw new IllegalArgumentException("Multiple SQL statements are not allowed");
        }
        return withoutTrailing;
    }

    public static void validate(String sql, Mode mode) {
        String normalized = normalize(sql);
        String upper = normalized.toUpperCase(Locale.ROOT);

        for (String keyword : ALWAYS_FORBIDDEN) {
            if (containsKeyword(upper, keyword)) {
                throw new IllegalArgumentException("Forbidden keyword: " + keyword);
            }
        }

        if (mode == Mode.READ) {
            if (!startsReadStatement(upper)) {
                throw new IllegalArgumentException("READ mode allows SELECT, SHOW, DESCRIBE, DESC, EXPLAIN only");
            }
            for (String keyword : READ_FORBIDDEN) {
                if (containsKeyword(upper, keyword)) {
                    throw new IllegalArgumentException("READ mode does not allow " + keyword);
                }
            }
            return;
        }

        if (!startsWriteStatement(upper)) {
            throw new IllegalArgumentException("WRITE mode allows INSERT, UPDATE, DELETE only");
        }
    }

    private static boolean startsReadStatement(String upper) {
        return upper.startsWith("SELECT")
                || upper.startsWith("SHOW")
                || upper.startsWith("DESCRIBE")
                || upper.startsWith("DESC ")
                || upper.startsWith("DESC\t")
                || upper.equals("DESC")
                || upper.startsWith("EXPLAIN");
    }

    private static boolean startsWriteStatement(String upper) {
        return upper.startsWith("INSERT")
                || upper.startsWith("UPDATE")
                || upper.startsWith("DELETE");
    }

    private static boolean containsKeyword(String upper, String keyword) {
        return Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b").matcher(upper).find();
    }
}
