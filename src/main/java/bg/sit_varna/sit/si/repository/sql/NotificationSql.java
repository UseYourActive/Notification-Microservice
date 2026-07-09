package bg.sit_varna.sit.si.repository.sql;

/**
 * Table and column names for native SQL against the notifications table (used by
 * claimBatch's atomic claim, which bypasses Panache/JPQL and the JPA metamodel).
 */
public final class NotificationSql {

    public static final String TABLE = "notifications";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_STATUS = "status";
    public static final String COLUMN_LOCKED_BY = "locked_by";
    public static final String COLUMN_LOCKED_AT = "locked_at";
    public static final String COLUMN_CREATED_AT = "created_at";

    private NotificationSql() {
    }
}
