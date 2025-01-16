package org.monxef.gbank.storage.impl;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.monxef.gbank.GBank;
import org.monxef.gbank.objects.PlayerProfile;
import org.monxef.gbank.objects.Transaction;
import org.monxef.gbank.storage.StorageHandler;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class JsonStorageHandler implements StorageHandler {
    private final File dataFolder;
    private final File transactionsFolder;
    private final Gson gson;
    private final Map<UUID, PlayerProfile> cache;
    private final Map<UUID, List<Transaction>> transactionCache;

    public JsonStorageHandler(GBank plugin) {
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        this.transactionsFolder = new File(plugin.getDataFolder(), "transactions");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        if (!transactionsFolder.exists()) {
            transactionsFolder.mkdirs();
        }

        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        this.cache = new ConcurrentHashMap<>();
        this.transactionCache = new ConcurrentHashMap<>();
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> loadProfile(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            // Check cache first
            if (cache.containsKey(playerId)) {
                return Optional.of(cache.get(playerId));
            }

            File playerFile = new File(dataFolder, playerId.toString() + ".json");
            if (!playerFile.exists()) {
                PlayerProfile newProfile = new PlayerProfile(playerId);
                cache.put(playerId, newProfile);
                return Optional.of(newProfile);
            }

            try (FileReader reader = new FileReader(playerFile)) {
                Type type = new TypeToken<Map<String, Double>>(){}.getType();
                Map<String, Double> balances = gson.fromJson(reader, type);

                PlayerProfile profile = new PlayerProfile(playerId);
                balances.forEach(profile::setBalance);

                cache.put(playerId, profile);
                return Optional.of(profile);
            } catch (IOException e) {
                e.printStackTrace();
                return Optional.empty();
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveProfile(PlayerProfile profile) {
        return CompletableFuture.runAsync(() -> {
            File playerFile = new File(dataFolder, profile.getPlayerId().toString() + ".json");

            try (FileWriter writer = new FileWriter(playerFile)) {
                gson.toJson(profile.getBalances(), writer);
                cache.put(profile.getPlayerId(), profile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveTransaction(UUID playerId, String currency, Transaction transaction) {
        return CompletableFuture.runAsync(() -> {
            File transactionFile = new File(transactionsFolder, playerId.toString() + "_" + currency + ".json");
            List<Transaction> transactions = loadTransactionsFromFile(transactionFile);
            transactions.add(transaction);

            try (FileWriter writer = new FileWriter(transactionFile)) {
                gson.toJson(transactions, writer);
                
                transactionCache.computeIfAbsent(playerId, k -> new ArrayList<>()).add(transaction);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public CompletableFuture<List<Transaction>> getTransactions(UUID playerId, String currency, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            if (transactionCache.containsKey(playerId)) {
                List<Transaction> cachedTransactions = transactionCache.get(playerId);
                return cachedTransactions.stream()
                        .limit(limit)
                        .toList();
            }

            File transactionFile = new File(transactionsFolder, playerId.toString() + "_" + currency + ".json");
            List<Transaction> transactions = loadTransactionsFromFile(transactionFile);
            
            transactionCache.put(playerId, transactions);
            
            return transactions.stream()
                    .limit(limit)
                    .toList();
        });
    }

    private List<Transaction> loadTransactionsFromFile(File file) {
        if (!file.exists()) {
            return new ArrayList<>();
        }

        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<List<Transaction>>(){}.getType();
            List<Transaction> transactions = gson.fromJson(reader, type);
            return transactions != null ? transactions : new ArrayList<>();
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void saveAll() {
        cache.values().forEach(profile -> {
            try {
                File playerFile = new File(dataFolder, profile.getPlayerId().toString() + ".json");
                try (FileWriter writer = new FileWriter(playerFile)) {
                    gson.toJson(profile.getBalances(), writer);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void clearCache() {
        cache.clear();
        transactionCache.clear();
    }
}