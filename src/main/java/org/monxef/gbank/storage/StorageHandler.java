package org.monxef.gbank.storage;

import org.monxef.gbank.objects.PlayerProfile;
import org.monxef.gbank.objects.Transaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface StorageHandler {
    CompletableFuture<Optional<PlayerProfile>> loadProfile(UUID playerId);
    CompletableFuture<Void> saveProfile(PlayerProfile profile);
    CompletableFuture<Void> saveTransaction(UUID playerId, String currency, Transaction transaction);
    CompletableFuture<List<Transaction>> getTransactions(UUID playerId, String currency, int limit);
}