package bg.sit_varna.sit.si.qacommons.db;

import dev.qacommons.db.PostgresDatabase;
import dev.qacommons.db.config.DbConfig;
import java.util.Optional;

/**
 * The one place this suite names the real {@code notifications} table -
 * qa-commons-db's {@link PostgresDatabase} stays table-agnostic by design.
 */
public final class NotificationsOracle {

    private final PostgresDatabase database;

    public NotificationsOracle(DbConfig config) {
        this.database = new PostgresDatabase(config);
    }

    public Optional<NotificationRow> findById(String id) {
        return database.queryOne(
                "SELECT id, recipient, channel FROM notifications WHERE id = ?",
                row -> new NotificationRow(row.getString("id"), row.getString("recipient"), row.getString("channel")),
                id);
    }
}
