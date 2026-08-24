package io.mo;

import junit.framework.TestCase;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ConsistencyCheckTest extends TestCase {
    public void testSqlExceptionReturnsNonZeroInOneShotMode() {
        int code = ConsistencyCheck.runChecks(
                properties(),
                0,
                factory(connection(statementThrowing(new SQLException("forced SQL failure")))),
                noSleep());

        assertEquals(1, code);
    }

    public void testSqlExceptionReturnsNonZeroInContinuousModeWithoutSleeping() {
        final AtomicBoolean slept = new AtomicBoolean(false);
        int code = ConsistencyCheck.runChecks(
                properties(),
                30,
                factory(connection(statementThrowing(new SQLException("forced SQL failure")))),
                new ConsistencyCheck.Sleeper() {
                    @Override
                    public void sleep(long milliseconds) {
                        slept.set(true);
                    }
                });

        assertEquals(1, code);
        assertFalse("failed generation must not sleep and continue", slept.get());
    }

    public void testConnectionExceptionReturnsNonZero() {
        int code = ConsistencyCheck.runChecks(
                properties(),
                0,
                new ConsistencyCheck.ConnectionFactory() {
                    @Override
                    public Connection connect(String url, Properties properties) throws SQLException {
                        throw new SQLException("forced connection failure");
                    }
                },
                noSleep());

        assertEquals(1, code);
    }

    public void testExceptionalRowReturnsNonZero() {
        final AtomicInteger queryNumber = new AtomicInteger();
        Statement statement = proxy(Statement.class, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                if ("executeQuery".equals(method.getName())) {
                    return resultSet(queryNumber.getAndIncrement() == 0);
                }
                return defaultValue(method.getReturnType());
            }
        });

        int code = ConsistencyCheck.runChecks(
                properties(), 0, factory(connection(statement)), noSleep());

        assertEquals(1, code);
        assertEquals(ConsistencyCheck.QUERIES.length, queryNumber.get());
    }

    public void testEmptyExceptionalSetsReturnZero() {
        final AtomicInteger queryNumber = new AtomicInteger();
        Statement statement = proxy(Statement.class, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                if ("executeQuery".equals(method.getName())) {
                    queryNumber.incrementAndGet();
                    return resultSet(false);
                }
                return defaultValue(method.getReturnType());
            }
        });

        int code = ConsistencyCheck.runChecks(
                properties(), 0, factory(connection(statement)), noSleep());

        assertEquals(0, code);
        assertEquals(ConsistencyCheck.QUERIES.length, queryNumber.get());
    }

    public void testIncompletePropertiesReturnNonZeroBeforeConnect() {
        final AtomicBoolean connected = new AtomicBoolean(false);
        Properties properties = properties();
        properties.remove("conn");
        int code = ConsistencyCheck.runChecks(
                properties,
                0,
                new ConsistencyCheck.ConnectionFactory() {
                    @Override
                    public Connection connect(String url, Properties properties) {
                        connected.set(true);
                        return null;
                    }
                },
                noSleep());

        assertEquals(1, code);
        assertFalse(connected.get());
    }

    private static Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("db", "mo");
        properties.setProperty("driver", "unused.by.injected.factory");
        properties.setProperty("conn", "jdbc:fake");
        properties.setProperty("user", "tester");
        properties.setProperty("password", "not-logged");
        return properties;
    }

    private static ConsistencyCheck.ConnectionFactory factory(final Connection connection) {
        return new ConsistencyCheck.ConnectionFactory() {
            @Override
            public Connection connect(String url, Properties properties) {
                return connection;
            }
        };
    }

    private static ConsistencyCheck.Sleeper noSleep() {
        return new ConsistencyCheck.Sleeper() {
            @Override
            public void sleep(long milliseconds) {
                fail("one-shot checker must not sleep");
            }
        };
    }

    private static Connection connection(final Statement statement) {
        return proxy(Connection.class, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                if ("createStatement".equals(method.getName())) {
                    return statement;
                }
                return defaultValue(method.getReturnType());
            }
        });
    }

    private static Statement statementThrowing(final SQLException failure) {
        return proxy(Statement.class, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("executeQuery".equals(method.getName())) {
                    throw failure;
                }
                return defaultValue(method.getReturnType());
            }
        });
    }

    private static ResultSet resultSet(final boolean oneExceptionalRow) {
        final AtomicInteger nextCalls = new AtomicInteger();
        return proxy(ResultSet.class, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                if ("next".equals(method.getName())) {
                    return oneExceptionalRow && nextCalls.getAndIncrement() == 0;
                }
                if ("getMetaData".equals(method.getName())) {
                    return metadata();
                }
                if ("getString".equals(method.getName())) {
                    return "unexpected";
                }
                return defaultValue(method.getReturnType());
            }
        });
    }

    private static ResultSetMetaData metadata() {
        return proxy(ResultSetMetaData.class, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                if ("getColumnCount".equals(method.getName())) {
                    return 1;
                }
                return defaultValue(method.getReturnType());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == Boolean.TYPE) {
            return false;
        }
        if (type == Character.TYPE) {
            return '\0';
        }
        if (type == Byte.TYPE) {
            return (byte) 0;
        }
        if (type == Short.TYPE) {
            return (short) 0;
        }
        if (type == Integer.TYPE) {
            return 0;
        }
        if (type == Long.TYPE) {
            return 0L;
        }
        if (type == Float.TYPE) {
            return 0.0f;
        }
        if (type == Double.TYPE) {
            return 0.0d;
        }
        return null;
    }
}
