package io.mo;

import org.apache.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class ConsistencyCheck {
    private static final Logger LOG = Logger.getLogger(ConsistencyCheck.class);
    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_FAILURE = 1;
    private static final int EXIT_USAGE = 2;
    private static final int MAX_ERROR_ROWS_PER_QUERY = 100;
    private static final int MAX_ERROR_CHARS_PER_QUERY = 64 * 1024;

    static final String[] QUERIES = new String[]{
            "(Select w_id, w_ytd from bmsql_warehouse) except (select d_w_id, sum(d_ytd) from bmsql_district group by d_w_id);",
            "(Select d_w_id, d_id, D_NEXT_O_ID - 1 from bmsql_district)  except (select o_w_id, o_d_id, max(o_id) from bmsql_oorder group by  o_w_id, o_d_id);",
            "(Select d_w_id, d_id, D_NEXT_O_ID - 1 from bmsql_district)  except (select no_w_id, no_d_id, max(no_o_id) from bmsql_new_order group by no_w_id, no_d_id);",
            "select * from (select (count(no_o_id)-(max(no_o_id)-min(no_o_id)+1)) as `diff` from bmsql_new_order group by no_w_id, no_d_id) as temp where `diff` != 0;",
            "(select o_w_id, o_d_id, sum(o_ol_cnt) from bmsql_oorder  group by o_w_id, o_d_id) except (select ol_w_id, ol_d_id, count(ol_o_id) from bmsql_order_line group by ol_w_id, ol_d_id);",
            "(select d_w_id, sum(d_ytd) from bmsql_district group by d_w_id)  except(Select w_id, w_ytd from bmsql_warehouse);",
            "select c_w_id, c_d_id, c_id,count(1) from bmsql_customer group by c_w_id, c_d_id, c_id having count(1) > 1 limit 10;",
            "select c_w_id, c_d_id, c_id,count(1) from bmsql_customer group by c_w_id, c_d_id, c_id having count(1) > 1 limit 10;",
            "select no_w_id, no_d_id, no_o_id, count(1) from bmsql_new_order group by no_w_id, no_d_id, no_o_id having count(1) > 1 limit 10;",
            "select o_w_id,o_d_id, o_id,count(1) from bmsql_oorder group by o_w_id, o_d_id, o_id having count(1) > 1 limit 10;",
            "select s_w_id, s_i_id,count(1) from bmsql_stock group by s_w_id, s_i_id having count(1) > 1 limit 10;"
    };

    interface ConnectionFactory {
        Connection connect(String url, Properties properties) throws SQLException;
    }

    interface Sleeper {
        void sleep(long milliseconds) throws InterruptedException;
    }

    static final class CheckResult {
        final Map<String, String> errors;

        CheckResult(Map<String, String> errors) {
            this.errors = errors;
        }

        boolean isSuccess() {
            return errors.isEmpty();
        }
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        if (args.length > 1) {
            LOG.error("usage: ConsistencyCheck [interval-seconds]");
            return EXIT_USAGE;
        }

        final int intervalSeconds;
        try {
            intervalSeconds = args.length == 0 ? 0 : Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            LOG.error("interval-seconds must be an integer");
            return EXIT_USAGE;
        }
        if (intervalSeconds < 0) {
            LOG.error("interval-seconds must not be negative");
            return EXIT_USAGE;
        }

        String propertiesPath = System.getProperty("prop");
        if (propertiesPath == null || propertiesPath.trim().isEmpty()) {
            LOG.error("The prop system property is required.");
            return EXIT_USAGE;
        }

        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(propertiesPath)) {
            properties.load(input);
        } catch (IOException e) {
            LOG.error("Could not load properties file.", e);
            return EXIT_FAILURE;
        }

        String driver = requiredProperty(properties, "driver");
        if (driver == null) {
            return EXIT_FAILURE;
        }
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            LOG.error("Could not find configured JDBC driver.", e);
            return EXIT_FAILURE;
        }

        return runChecks(
                properties,
                intervalSeconds,
                new ConnectionFactory() {
                    @Override
                    public Connection connect(String url, Properties dbProperties) throws SQLException {
                        return DriverManager.getConnection(url, dbProperties);
                    }
                },
                new Sleeper() {
                    @Override
                    public void sleep(long milliseconds) throws InterruptedException {
                        Thread.sleep(milliseconds);
                    }
                });
    }

    static int runChecks(
            Properties properties,
            int intervalSeconds,
            ConnectionFactory connectionFactory,
            Sleeper sleeper) {
        if (intervalSeconds < 0) {
            return EXIT_USAGE;
        }
        String database = requiredProperty(properties, "db");
        String url = requiredProperty(properties, "conn");
        String user = requiredProperty(properties, "user");
        String password = properties.getProperty("password");
        if (database == null || url == null || user == null || password == null) {
            LOG.error("Checker properties are incomplete.");
            return EXIT_FAILURE;
        }

        Properties dbProperties = new Properties();
        dbProperties.setProperty("user", user);
        dbProperties.setProperty("password", password);

        try (Connection connection = connectionFactory.connect(url, dbProperties)) {
            do {
                CheckResult result = verifyOnce(connection);
                if (!result.isSuccess()) {
                    logConsistencyErrors(result.errors);
                    return EXIT_FAILURE;
                }
                LOG.info("Consistency verification successfully.");
                if (intervalSeconds == 0) {
                    return EXIT_SUCCESS;
                }
                sleeper.sleep(intervalSeconds * 1000L);
            } while (true);
        } catch (SQLException e) {
            // A SQL exception is a failed checker attempt.  Continuous callers
            // may restart a new generation, but this process must never log the
            // exception and then report success.
            LOG.error("Consistency verification failed with a SQL exception.", e);
            return EXIT_FAILURE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Consistency verification was interrupted.", e);
            return EXIT_FAILURE;
        }
    }

    static CheckResult verifyOnce(Connection connection) throws SQLException {
        Map<String, String> errors = new LinkedHashMap<String, String>();
        try (Statement statement = connection.createStatement()) {
            for (String query : QUERIES) {
                try (ResultSet resultSet = statement.executeQuery(query)) {
                    String exceptionalRows = readExceptionalRows(resultSet);
                    if (!exceptionalRows.isEmpty()) {
                        errors.put(query, exceptionalRows);
                    }
                }
            }
        }
        return new CheckResult(errors);
    }

    private static String readExceptionalRows(ResultSet resultSet) throws SQLException {
        StringBuilder output = new StringBuilder();
        ResultSetMetaData metadata = null;
        int rowCount = 0;
        boolean truncated = false;
        while (resultSet.next()) {
            if (metadata == null) {
                metadata = resultSet.getMetaData();
            }
            rowCount++;
            if (rowCount > MAX_ERROR_ROWS_PER_QUERY || output.length() >= MAX_ERROR_CHARS_PER_QUERY) {
                truncated = true;
                continue;
            }
            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                if (column > 1) {
                    output.append('\t');
                }
                output.append(resultSet.getString(column));
            }
            output.append('\n');
        }
        if (truncated) {
            output.append("... exceptional rows truncated ...\n");
        }
        return output.toString();
    }

    private static void logConsistencyErrors(Map<String, String> errors) {
        for (Map.Entry<String, String> entry : errors.entrySet()) {
            LOG.error("Consistency verification failed for sql : " + entry.getKey());
            LOG.error("The exceptional result are :\n" + entry.getValue());
        }
    }

    private static String requiredProperty(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            LOG.error("Missing required checker property: " + name);
            return null;
        }
        return value;
    }
}
