package org.monxef.gbank.api;

import org.monxef.gbank.GBank;
import org.monxef.gbank.managers.PluginManager;
import org.monxef.gbank.objects.PlayerProfile;

import java.util.HashMap;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GBankAPI {
    private static GBank plugin;

    public static void setPlugin(GBank plugin) {
        GBankAPI.plugin = plugin;
    }

    /**
     * Get a player's balance for a specific currency
     *
     * @param playerId The UUID of the player
     * @param currency The currency code
     * @return CompletableFuture containing the balance
     * @throws IllegalArgumentException if currency doesn't exist
     */
    public static CompletableFuture<Double> getBalance(UUID playerId, String currency) {
        if (!PluginManager.getInstance().getCurrenciesManager().getManager().containsKey(currency)) {
            throw new IllegalArgumentException("Invalid currency: " + currency);
        }

        return plugin.getStorageHandler()
                .loadProfile(playerId)
                .thenApply(optionalProfile ->
                        optionalProfile.map(profile -> profile.getBalance(currency))
                                .orElse(0.0));
    }

    /**
     * Get all balances for a player
     *
     * @param playerId The UUID of the player
     * @return CompletableFuture containing a map of currency codes to balances
     */
    public static CompletableFuture<Map<String, Double>> getAllBalances(UUID playerId) {
        return plugin.getStorageHandler()
                .loadProfile(playerId)
                .thenApply(optionalProfile ->
                        optionalProfile.map(PlayerProfile::getBalances)
                                .orElse(new HashMap<>()));
    }

    /**
     * Transfer currency between players
     *
     * @param from Source player UUID
     * @param to Destination player UUID
     * @param currency Currency code
     * @param amount Amount to transfer
     * @param applyTax Whether to apply transaction tax
     * @return CompletableFuture containing transaction success status
     */
    public static CompletableFuture<TransactionResult> transfer(
            UUID from, UUID to, String currency, double amount, boolean applyTax) {
        if (!PluginManager.getInstance().getCurrenciesManager().getManager().containsKey(currency)) {
            return CompletableFuture.completedFuture(
                    TransactionResult.failure("Invalid currency"));
        }

        double taxAmount = applyTax ? amount * PluginManager.getInstance().getTaxRate() : 0;
        double finalAmount = amount - taxAmount;

        return plugin.getStorageHandler().loadProfile(from)
                .thenCompose(fromProfile -> {
                    if (!fromProfile.isPresent()) {
                        return CompletableFuture.completedFuture(
                                TransactionResult.failure("Source profile not found"));
                    }

                    if (fromProfile.get().getBalance(currency) < amount) {
                        return CompletableFuture.completedFuture(
                                TransactionResult.failure("Insufficient funds"));
                    }

                    return plugin.getStorageHandler().loadProfile(to)
                            .thenCompose(toProfile -> {
                                if (!toProfile.isPresent()) {
                                    return CompletableFuture.completedFuture(
                                            TransactionResult.failure("Destination profile not found"));
                                }

                                PlayerProfile source = fromProfile.get();
                                PlayerProfile dest = toProfile.get();

                                source.removeBalance(currency, amount);
                                dest.addBalance(currency, finalAmount);

                                CompletableFuture<Void> saveSrc = plugin.getStorageHandler()
                                        .saveProfile(source);
                                CompletableFuture<Void> saveDst = plugin.getStorageHandler()
                                        .saveProfile(dest);

                                return CompletableFuture.allOf(saveSrc, saveDst)
                                        .thenApply(v -> TransactionResult.success(taxAmount));
                            });
                });
    }
}

class TransactionResult {
    private final boolean success;
    private final String message;
    private final double taxAmount;

    private TransactionResult(boolean success, String message, double taxAmount) {
        this.success = success;
        this.message = message;
        this.taxAmount = taxAmount;
    }

    public static TransactionResult success(double taxAmount) {
        return new TransactionResult(true, "Success", taxAmount);
    }

    public static TransactionResult failure(String message) {
        return new TransactionResult(false, message, 0);
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public double getTaxAmount() { return taxAmount; }
}