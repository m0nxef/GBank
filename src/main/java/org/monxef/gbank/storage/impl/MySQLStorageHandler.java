package org.monxef.gbank.storage.impl;

import org.monxef.gbank.objects.PlayerProfile;
import org.monxef.gbank.objects.Transaction;
import org.monxef.gbank.enums.TransactionType;
import org.monxef.gbank.storage.StorageHandler;

import java.sql.ResultSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MySQLStorageHandler implements StorageHandler {
    private final com.zaxxer.hikari.HikariDataSource dataSource;

    public MySQLStorageHandler(String host, int port, String database,
                               String username, String password, boolean useSsl) {
        com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();

        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s", host, port, database);
        if (useSsl) {
            jdbcUrl += "?useSSL=true&requireSSL=true&verifyServerCertificate=true";
        }

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);

        if (useSsl) {
            config.addDataSourceProperty("sslMode", "REQUIRED");
            config.addDataSourceProperty("trustCertificateKeyStoreUrl", "file:/path/to/truststore.jks");
            config.addDataSourceProperty("trustCertificateKeyStorePassword", "truststore_password");
        }

        this.dataSource = new com.zaxxer.hikari.HikariDataSource(config);

        initializeTables();
    }
    private void initializeTables() {
        String createProfilesTable = """
            CREATE TABLE IF NOT EXISTS profiles (
                uuid VARCHAR(36) PRIMARY KEY,
                last_updated BIGINT
            )
        """;

        String createBalancesTable = """
            CREATE TABLE IF NOT EXISTS balances (
                uuid VARCHAR(36),
                currency VARCHAR(32),
                amount DECIMAL(20, 2),
                PRIMARY KEY (uuid, currency),
                FOREIGN KEY (uuid) REFERENCES profiles(uuid) ON DELETE CASCADE
            )
        """;

        String createTransactionsTable = """
            CREATE TABLE IF NOT EXISTS transactions (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                player_id VARCHAR(36),
                currency VARCHAR(32),
                type VARCHAR(32),
                amount DECIMAL(20, 2),
                details TEXT,
                timestamp BIGINT,
                INDEX idx_player_currency_time (player_id, currency, timestamp DESC),
                FOREIGN KEY (player_id) REFERENCES profiles(uuid) ON DELETE CASCADE
            )
        """;

        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute(createProfilesTable);
            stmt.execute(createBalancesTable);
            stmt.execute(createTransactionsTable);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> loadProfile(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try (var conn = dataSource.getConnection();
                 var stmt = conn.prepareStatement(
                         "SELECT currency, amount FROM balances WHERE uuid = ?")) {

                stmt.setString(1, playerId.toString());
                PlayerProfile profile = new PlayerProfile(playerId);

                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String currency = rs.getString("currency");
                        double amount = rs.getDouble("amount");
                        profile.setBalance(currency, amount);
                    }
                }

                return Optional.of(profile);
            } catch (Exception e) {
                e.printStackTrace();
                return Optional.empty();
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveProfile(PlayerProfile profile) {
        return CompletableFuture.runAsync(() -> {
            try (var conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try {
                    try (var stmt = conn.prepareStatement(
                            "INSERT INTO profiles (uuid, last_updated) VALUES (?, ?) " +
                                    "ON DUPLICATE KEY UPDATE last_updated = ?")) {

                        long now = System.currentTimeMillis();
                        stmt.setString(1, profile.getPlayerId().toString());
                        stmt.setLong(2, now);
                        stmt.setLong(3, now);
                        stmt.executeUpdate();
                    }

                    try (var stmt = conn.prepareStatement(
                            "DELETE FROM balances WHERE uuid = ?")) {
                        stmt.setString(1, profile.getPlayerId().toString());
                        stmt.executeUpdate();
                    }

                    try (var stmt = conn.prepareStatement(
                            "INSERT INTO balances (uuid, currency, amount) VALUES (?, ?, ?)")) {

                        for (Map.Entry<String, Double> entry : profile.getBalances().entrySet()) {
                            stmt.setString(1, profile.getPlayerId().toString());
                            stmt.setString(2, entry.getKey());
                            stmt.setDouble(3, entry.getValue());
                            stmt.executeUpdate();
                        }
                    }

                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveTransaction(UUID playerId, String currency, Transaction transaction) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO transactions 
                (player_id, currency, type, amount, details, timestamp)
                VALUES (?, ?, ?, ?, ?, ?)
            """;

            try (var conn = dataSource.getConnection();
                 var stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, playerId.toString());
                stmt.setString(2, currency);
                stmt.setString(3, transaction.getType().toString());
                stmt.setDouble(4, transaction.getAmount());
                stmt.setString(5, transaction.getDetails());
                stmt.setLong(6, transaction.getTimestamp());

                stmt.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public CompletableFuture<List<Transaction>> getTransactions(UUID playerId, String currency, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT type, amount, details, timestamp
                FROM transactions
                WHERE player_id = ? AND currency = ?
                ORDER BY timestamp DESC
                LIMIT ?
            """;

            List<Transaction> transactions = new ArrayList<>();

            try (var conn = dataSource.getConnection();
                 var stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, playerId.toString());
                stmt.setString(2, currency);
                stmt.setInt(3, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Transaction transaction = new Transaction(
                            TransactionType.valueOf(rs.getString("type")),
                            rs.getDouble("amount"),
                            rs.getString("details")
                        );
                        transactions.add(transaction);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            return transactions;
        });
    }

    public void close() {
        dataSource.close();
    }
}