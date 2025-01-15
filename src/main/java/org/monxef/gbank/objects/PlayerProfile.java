package org.monxef.gbank.objects;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerProfile {
    @Getter
    private final UUID playerId;
    private final Map<String, Double> balances;

    public PlayerProfile(UUID playerId) {
        this.playerId = playerId;
        this.balances = new HashMap<>();
    }

    public double getBalance(String currency) {
        return balances.getOrDefault(currency, 0.0);
    }

    public void setBalance(String currency, double amount) {
        balances.put(currency, Math.max(0, amount));
    }

    public void addBalance(String currency, double amount) {
        setBalance(currency, getBalance(currency) + amount);
    }

    public boolean removeBalance(String currency, double amount) {
        double currentBalance = getBalance(currency);
        if (currentBalance >= amount) {
            setBalance(currency, currentBalance - amount);
            return true;
        }
        return false;
    }

    public Map<String, Double> getBalances() {
        return new HashMap<>(balances);
    }

    public boolean hasBalance(String currencyCode, double amount) {
        if (amount < 0) return false;
        return getBalance(currencyCode) >= amount;
    }
}