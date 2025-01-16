package org.monxef.gbank.tasks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.monxef.gbank.GBank;
import org.monxef.gbank.enums.ConfigurationType;
import org.monxef.gbank.managers.PluginManager;
import org.monxef.gbank.objects.PlayerProfile;
import org.monxef.gbank.wrappers.ConfigWrapper;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AutomaticPaymentTask extends BukkitRunnable {
    private final GBank plugin;
    private final double amount;
    private final String defaultCurrency;

    public AutomaticPaymentTask(GBank plugin, double amount) {
        this.plugin = plugin;
        this.amount = amount;
        this.defaultCurrency = plugin.getConfig().getString("default_currency", "dollars");
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            processPayment(player.getUniqueId());
        }
    }

    private void processPayment(UUID playerId) {
        CompletableFuture<Optional<PlayerProfile>> futureProfile = plugin.getStorageHandler().loadProfile(playerId);
        
        futureProfile.thenAccept(optionalProfile -> {
            PlayerProfile profile = optionalProfile.orElseGet(() -> new PlayerProfile(playerId));
            profile.addBalance(defaultCurrency, amount);
            
            plugin.getStorageHandler().saveProfile(profile).thenRun(() -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    String message = ConfigWrapper.valueOf(ConfigurationType.MESSAGE).getConfig().getString("automatic_payment_received",
                        "You received {amount} {currency} from automatic payment!")
                        .replace("{amount}", String.format("%.2f", amount))
                        .replace("{currency}", PluginManager.getInstance().getCurrenciesManager().get(defaultCurrency).getDisplayName());
                    
                    player.sendMessage(message);
                }
            });
        });
    }
}
