package bg.sit_varna.sit.si.qacommons.db;

import dev.qacommons.db.PostgresDatabase;
import dev.qacommons.db.config.DbConfig;
import java.util.List;
import java.util.Set;

/**
 * Guards against a row assertion silently passing against the wrong
 * database (e.g. {@code QA_DB_*} pointed at some other Postgres that
 * happens to be listening). A successful check is cached for the rest of
 * the JVM's life so repeat callers short-circuit; a failed check is never
 * cached, so every caller keeps failing loudly until it's fixed.
 */
public final class SchemaFingerprint {

    /**
     * This repo's actual applied migrations - src/main/resources/db/migrations.
     */
    private static final Set<String> KNOWN_MIGRATIONS = Set.of("1.0.0", "1.0.1", "1.0.2", "1.0.3");

    private static volatile boolean verified = false;

    private SchemaFingerprint() {
    }

    public static synchronized void verifyOnce(DbConfig config) {
        if (verified) {
            return;
        }

        PostgresDatabase database = new PostgresDatabase(config);
        List<String> appliedVersions = database.queryList(
                "SELECT version FROM flyway_schema_history WHERE success = true",
                row -> row.getString("version"));

        if (!appliedVersions.containsAll(KNOWN_MIGRATIONS)) {
            throw new IllegalStateException(
                    "Database at %s does not look like this service's own schema: expected migrations %s, found %s"
                            .formatted(config.jdbcUrl(), KNOWN_MIGRATIONS, appliedVersions));
        }

        verified = true;
    }
}
