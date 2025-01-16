package org.monxef.gbank.storage.impl;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.monxef.gbank.objects.PlayerProfile;
import org.monxef.gbank.objects.Transaction;
import org.monxef.gbank.enums.TransactionType;
import org.monxef.gbank.storage.StorageHandler;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MongoDBStorageHandler implements StorageHandler {
    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final MongoCollection<Document> profiles;
    private final MongoCollection<Document> transactions;

    public MongoDBStorageHandler(String uri) {
        this.mongoClient = MongoClients.create(uri);
        this.database = mongoClient.getDatabase("gbank");
        this.profiles = database.getCollection("profiles");
        this.transactions = database.getCollection("transactions");

        profiles.createIndex(new Document("uuid", 1));
        transactions.createIndex(new Document("playerId", 1).append("currency", 1).append("timestamp", -1));
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> loadProfile(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            Document query = new Document("uuid", playerId.toString());
            Document doc = profiles.find(query).first();

            if (doc == null) {
                return Optional.of(new PlayerProfile(playerId));
            }

            PlayerProfile profile = new PlayerProfile(playerId);
            Document balances = doc.get("balances", Document.class);

            balances.forEach((currency, value) ->
                    profile.setBalance(currency, ((Number) value).doubleValue()));

            return Optional.of(profile);
        });
    }

    @Override
    public CompletableFuture<Void> saveProfile(PlayerProfile profile) {
        return CompletableFuture.runAsync(() -> {
            Document query = new Document("uuid", profile.getPlayerId().toString());
            Document update = new Document("$set", new Document()
                    .append("uuid", profile.getPlayerId().toString())
                    .append("balances", new Document(profile.getBalances())));

            profiles.updateOne(query, update, new com.mongodb.client.model.UpdateOptions().upsert(true));
        });
    }

    @Override
    public CompletableFuture<Void> saveTransaction(UUID playerId, String currency, Transaction transaction) {
        return CompletableFuture.runAsync(() -> {
            Document doc = new Document()
                    .append("playerId", playerId.toString())
                    .append("currency", currency)
                    .append("type", transaction.getType().toString())
                    .append("amount", transaction.getAmount())
                    .append("description", transaction.getDetails())
                    .append("timestamp", transaction.getTimestamp());

            transactions.insertOne(doc);
        });
    }

    @Override
    public CompletableFuture<List<Transaction>> getTransactions(UUID playerId, String currency, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            Document query = new Document()
                    .append("playerId", playerId.toString())
                    .append("currency", currency);

            List<Transaction> result = new ArrayList<>();
            transactions.find(query)
                    .sort(Sorts.descending("timestamp"))
                    .limit(limit)
                    .forEach(doc -> {
                        Transaction transaction = new Transaction(
                            TransactionType.valueOf(doc.getString("type")),
                            doc.getDouble("amount"),
                            doc.getString("description")
                        );
                        result.add(transaction);
                    });

            return result;
        });
    }

    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}