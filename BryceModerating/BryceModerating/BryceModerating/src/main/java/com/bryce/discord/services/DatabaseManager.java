package com.bryce.discord.services;

import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.locks.ReentrantLock;

public class DatabaseManager {
    private static final Dotenv dotenv = loadDotenv();
    private static final String URL = getDatabaseUrl();
    private static final ReentrantLock lock = new ReentrantLock();
    private static volatile boolean isInitialized = false;

    private static Dotenv loadDotenv() {
        String[] locations = {
            ".env",
            "../.env",
            "../../.env",
            System.getProperty("user.home") + "/.env",
            System.getProperty("user.dir") + "/.env",
            System.getProperty("user.dir") + "/../.env"
        };

        for (String location : locations) {
            Path envPath = Paths.get(location);
            if (Files.exists(envPath)) {
                try {
                    Path absolutePath = envPath.toAbsolutePath();
                    Dotenv dotenv = Dotenv.configure()
                            .directory(absolutePath.getParent().toString())
                            .filename(absolutePath.getFileName().toString())
                            .load();
                    System.out.println("[DatabaseManager] ✅ Loaded .env file from: " + absolutePath);
                    return dotenv;
                } catch (Exception e) {
                    System.out.println("[DatabaseManager] ⚠️ Failed to load .env from " + envPath.toAbsolutePath() + ": " + e.getMessage());
                }
            }
        }

        System.out.println("[DatabaseManager] ⚠️ No .env file found in standard locations");
        try {
            return Dotenv.configure().ignoreIfMissing().load();
        } catch (Exception e) {
            System.out.println("[DatabaseManager] Using default configuration");
            return null;
        }
    }

    private static String getDatabaseUrl() {
        String propDbPath = System.getProperty("DATABASE_PATH");
        if (propDbPath != null && !propDbPath.isEmpty()) {
            System.out.println("[DatabaseManager] Using DATABASE_PATH from system property");
            return propDbPath;
        }

        String envDbPath = System.getenv("DATABASE_PATH");
        if (envDbPath != null && !envDbPath.isEmpty()) {
            System.out.println("[DatabaseManager] Using DATABASE_PATH from environment variable");
            return envDbPath;
        }

        if (dotenv != null) {
            String dbPath = dotenv.get("DATABASE_PATH");
            if (dbPath != null) {
                System.out.println("[DatabaseManager] Using DATABASE_PATH from .env file");
                return dbPath;
            }
        }

        System.err.println("[DatabaseManager] WARNING: No DATABASE_PATH found, using SQLite default");
        return "jdbc:sqlite:modbot.db";
    }

    public static Connection getConnection() throws SQLException {
        if (!isInitialized) {
            initializeDatabase();
        }

        return DriverManager.getConnection(URL);
    }

    private static void initializeDatabase() {
        lock.lock();
        try {
            if (isInitialized) return;

            try (Connection testConn = DriverManager.getConnection(URL)) {
                try (Statement stmt = testConn.createStatement()) {
                    stmt.execute("SELECT 1");
                    System.out.println("[DatabaseManager] Database connection established successfully");
                }
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Database connection test failed: " + e.getMessage());
                handleDatabaseLock();
            }

            isInitialized = true;
        } finally {
            lock.unlock();
        }
    }

    private static void handleDatabaseLock() {
        System.out.println("[DatabaseManager] Attempting to resolve database lock...");

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Only handle SQLite-specific database locks
        if (URL.startsWith("jdbc:sqlite:")) {
            try (Connection conn = DriverManager.getConnection(URL + "?journal_mode=DELETE")) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA journal_mode = WAL");
                    System.out.println("[DatabaseManager] Database lock resolved");
                }
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Could not resolve database lock: " + e.getMessage());
                System.err.println("[DatabaseManager] You may need to restart the application or check for other processes using the database");
            }
        } else {
            System.out.println("[DatabaseManager] PostgreSQL detected - skipping SQLite-specific lock handling");
        }
    }

    public static <T> T executeWithRetry(DatabaseOperation<T> operation) throws SQLException {
        int maxRetries = 4;
        SQLException lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try (Connection conn = getConnection()) {
                return operation.execute(conn);
            } catch (SQLException e) {
                lastException = e;
                if (attempt < maxRetries && (e.getMessage().contains("database is locked") || e.getMessage().contains("Connection to") && e.getMessage().contains("refused"))) {
                    System.err.println("[DatabaseManager] Database error, retrying... (attempt " + attempt + "/" + maxRetries + "): " + e.getMessage());
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Interrupted during retry", ie);
                    }
                } else {
                    throw e;
                }
            }
        }

        throw lastException;
    }

    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T execute(Connection conn) throws SQLException;
    }
}


