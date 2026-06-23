package com.turtle.tutiencore.core.storage;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerProgressDatabaseTest {

    @Test
    void createsTableForMinimalPlayerProgress() throws Exception {
        RecordingDataSource dataSource = new RecordingDataSource();
        PlayerProgressDatabase database = new PlayerProgressDatabase(dataSource);

        database.initialize();

        assertEquals("CREATE TABLE IF NOT EXISTS tutien_player_progress ("
                + "uuid VARCHAR(36) PRIMARY KEY, "
                + "player_name VARCHAR(16) NOT NULL, "
                + "tuvi DOUBLE NOT NULL, "
                + "realm_id INT NOT NULL, "
                + "sub_realm VARCHAR(32) NOT NULL)", dataSource.statements.get(0));
    }

    @Test
    void upsertsOnlyRequestedPlayerProgressFields() throws Exception {
        RecordingDataSource dataSource = new RecordingDataSource();
        PlayerProgressDatabase database = new PlayerProgressDatabase(dataSource);
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000123");

        database.saveProgress(uuid, "Steve", 123.5, 4, "HAU_KY");

        assertEquals("INSERT INTO tutien_player_progress "
                + "(uuid, player_name, tuvi, realm_id, sub_realm) VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), "
                + "tuvi = VALUES(tuvi), realm_id = VALUES(realm_id), sub_realm = VALUES(sub_realm)",
                dataSource.preparedSql.get(0));
        assertEquals(List.of(uuid.toString(), "Steve", 123.5, 4, "HAU_KY"), dataSource.parameters);
    }

    private static final class RecordingDataSource implements DataSource {
        private final List<String> statements = new ArrayList<>();
        private final List<String> preparedSql = new ArrayList<>();
        private final List<Object> parameters = new ArrayList<>();

        @Override
        public Connection getConnection() {
            return proxy(Connection.class, (proxy, method, args) -> switch (method.getName()) {
                case "createStatement" -> statementProxy();
                case "prepareStatement" -> {
                    preparedSql.add((String) args[0]);
                    yield preparedStatementProxy();
                }
                case "close" -> null;
                case "isClosed" -> false;
                case "unwrap" -> null;
                case "isWrapperFor" -> false;
                default -> defaultValue(method.getReturnType());
            });
        }

        private Statement statementProxy() {
            return proxy(Statement.class, (proxy, method, args) -> switch (method.getName()) {
                case "executeUpdate" -> {
                    statements.add((String) args[0]);
                    yield 1;
                }
                case "close" -> null;
                case "unwrap" -> null;
                case "isWrapperFor" -> false;
                default -> defaultValue(method.getReturnType());
            });
        }

        private PreparedStatement preparedStatementProxy() {
            return proxy(PreparedStatement.class, (proxy, method, args) -> switch (method.getName()) {
                case "setString", "setDouble", "setInt" -> {
                    parameters.add(args[1]);
                    yield null;
                }
                case "executeUpdate" -> 1;
                case "close" -> null;
                case "unwrap" -> null;
                case "isWrapperFor" -> false;
                default -> defaultValue(method.getReturnType());
            });
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        private static Object defaultValue(Class<?> type) {
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == double.class) return 0D;
            return null;
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
        }
    }
}
