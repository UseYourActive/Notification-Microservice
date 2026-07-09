package bg.sit_varna.sit.si.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives Flyway directly (no Quarkus boot) against a hand-seeded schema that
 * reproduces what every real environment actually has today: a database
 * built by Hibernate's schema-management=update - notification_attempts.id
 * already bigint, already backed by a notification_attempts_seq with
 * increment 50, no old uuid column - with Flyway never having run before.
 * baseline-on-migrate + baseline-version=1.0.2 (see application.properties)
 * routes exactly this shape through V1.0.3 as the first migration that
 * actually executes. Proves it's a safe no-op: the pre-existing row's
 * id is untouched and the sequence is never regressed.
 */
class NotificationAttemptsMigrationReconciliationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("reconciliation_test")
                    .withUsername("test")
                    .withPassword("test");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    void migrationsAreSafeNoOpsAgainstAnAlreadyHibernateBuiltSchema() throws Exception {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE notifications (
                        id VARCHAR(255) NOT NULL PRIMARY KEY
                    )
                    """);
            statement.execute("""
                    CREATE TABLE notification_attempts (
                        id BIGINT NOT NULL PRIMARY KEY,
                        notification_id VARCHAR(255) NOT NULL,
                        FOREIGN KEY (notification_id) REFERENCES notifications(id)
                    )
                    """);
            statement.execute("CREATE SEQUENCE notification_attempts_seq INCREMENT BY 50");
            // Simulates Hibernate's pooled allocator already having reserved a
            // block beyond the one row actually persisted (a normal rollback
            // gap) - this is the exact condition that must not regress.
            statement.execute("SELECT setval('notification_attempts_seq', 150)");
            statement.execute("INSERT INTO notifications (id) VALUES ('seed-1')");
            statement.execute("INSERT INTO notification_attempts (id, notification_id) VALUES (1, 'seed-1')");
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migrations")
                .baselineOnMigrate(true)
                .baselineVersion("1.0.2")
                .load()
                .migrate();

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {

            try (ResultSet rs = statement.executeQuery(
                    "select data_type from information_schema.columns " +
                            "where table_name = 'notification_attempts' and column_name = 'id'")) {
                assertTrue(rs.next());
                assertEquals("bigint", rs.getString(1));
            }

            try (ResultSet rs = statement.executeQuery(
                    "select increment_by, last_value from pg_sequences " +
                            "where sequencename = 'notification_attempts_seq'")) {
                assertTrue(rs.next());
                assertEquals(50, rs.getInt(1), "increment must stay 50, not be reset");
                assertTrue(rs.getLong(2) >= 150,
                        "the sequence must never regress below a value it had already reserved");
            }

            try (ResultSet rs = statement.executeQuery("select id from notification_attempts")) {
                assertTrue(rs.next());
                assertEquals(1L, rs.getLong(1), "the pre-existing row's id must be untouched");
            }

            try (ResultSet rs = statement.executeQuery(
                    "select count(*) from flyway_schema_history " +
                            "where version = '1.0.3' and success = true")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1),
                        "V1.0.3 must actually run (not skipped) - it's above baseline-version 1.0.2");
            }
        }
    }

    /**
     * The advance-only setval guard has two sides: never regress (covered
     * above), but also actually raise the sequence when the table's real data
     * legitimately gets ahead of it - e.g. a manual/bulk data load that
     * inserts explicit id values without going through nextval() at all.
     * Runs against its own container (not the shared one above) since Flyway
     * only ever migrates a given schema once.
     */
    @Test
    void advanceOnlyRaisesTheSequenceWhenTableDataIsAheadOfIt() throws Exception {
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("reconciliation_advance_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();
        try {
            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE notifications (
                            id VARCHAR(255) NOT NULL PRIMARY KEY
                        )
                        """);
                statement.execute("""
                        CREATE TABLE notification_attempts (
                            id BIGINT NOT NULL PRIMARY KEY,
                            notification_id VARCHAR(255) NOT NULL,
                            FOREIGN KEY (notification_id) REFERENCES notifications(id)
                        )
                        """);
                statement.execute("CREATE SEQUENCE notification_attempts_seq INCREMENT BY 50");
                // Sequence starts fresh (last_value 1), but a row exists with an
                // id far ahead of it - e.g. a manual/bulk data load that bypassed
                // nextval() entirely.
                statement.execute("INSERT INTO notifications (id) VALUES ('seed-1')");
                statement.execute("INSERT INTO notification_attempts (id, notification_id) VALUES (500, 'seed-1')");
            }

            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migrations")
                    .baselineOnMigrate(true)
                    .baselineVersion("1.0.2")
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "select last_value from pg_sequences where sequencename = 'notification_attempts_seq'")) {
                assertTrue(rs.next());
                assertTrue(rs.getLong(1) >= 500,
                        "the sequence must be raised past a row whose id already exceeds it, or the next "
                                + "nextval() call would collide with that row");
            }
        } finally {
            postgres.stop();
        }
    }

    private static Connection openConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
