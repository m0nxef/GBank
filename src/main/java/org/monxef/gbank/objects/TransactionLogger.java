package org.monxef.gbank.objects;

import org.monxef.gbank.GBank;
import org.monxef.gbank.storage.StorageHandler;
import org.monxef.gbank.storage.impl.JsonStorageHandler;
import org.monxef.gbank.storage.impl.MongoDBStorageHandler;
import org.monxef.gbank.storage.impl.MySQLStorageHandler;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class TransactionLogger {
    private final GBank plugin;
    private final StorageHandler storage;

    public TransactionLogger(GBank plugin) {
        this.plugin = plugin;
        this.storage = initializeStorage();
    }

    private StorageHandler initializeStorage() {
        String storageType = plugin.getConfig().getString("transaction_history.storage", "SAME");
        // Initialize separate storage based on main config structure
        String mainStorageType = plugin.getConfig().getString("storage.type", "json");

        return switch (mainStorageType.toLowerCase()) {
            case "mysql" -> {
                String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
                int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
                String database = plugin.getConfig().getString("storage.mysql.database", "gbank");
                String username = plugin.getConfig().getString("storage.mysql.username", "root");
                String password = plugin.getConfig().getString("storage.mysql.password", "password");
                boolean ssl = plugin.getConfig().getBoolean("storage.mysql.ssl",false);
                yield new MySQLStorageHandler(host, port, database, username, password,ssl);
            }
            case "mongodb" -> {
                String uri = plugin.getConfig().getString("storage.mongodb.uri", "mongodb://localhost:27017");
                String database = plugin.getConfig().getString("storage.mongodb.database", "gbank");
                yield new MongoDBStorageHandler(uri + "/" + database);
            }
            case "json" -> new JsonStorageHandler(plugin);
            default -> plugin.getStorageHandler(); // Fallback to main storage
        };
    }
    public CompletableFuture<Void> logTransaction(UUID playerId, String currency, Transaction transaction) {
        return storage.saveTransaction(playerId, currency, transaction)
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to log transaction: " + throwable.getMessage());
                    throwable.printStackTrace();
                    return null;
                });
    }

    public CompletableFuture<List<Transaction>> getTransactions(UUID playerId, String currency, int limit) {
        return storage.getTransactions(playerId, currency, limit)
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to get transactions: " + throwable.getMessage());
                    throwable.printStackTrace();
                    return Collections.emptyList();
                });
    }
}