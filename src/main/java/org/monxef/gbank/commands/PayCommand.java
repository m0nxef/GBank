package org.monxef.gbank.commands;


import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.monxef.gbank.GBank;
import org.monxef.gbank.managers.PluginManager;
import org.monxef.gbank.objects.PlayerProfile;

import org.monxef.gbank.objects.Currency;
import org.monxef.gbank.utils.MessagesUtils;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class PayCommand implements CommandExecutor {
    private final GBank plugin;

    public PayCommand(GBank plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessagesUtils.getMessage("player_only"));
            return true;
        }

        if (!sender.hasPermission("gbank.pay")) {
            sender.sendMessage(MessagesUtils.getMessage("no_permission"));
            return true;
        }

        if (args.length != 3) {
            sender.sendMessage(MessagesUtils.getMessage("pay_usage"));
            return true;
        }

        String targetName = args[0];
        String currencyCode = args[1];
        double amount;

        try {
            amount = Double.parseDouble(args[2]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(MessagesUtils.getMessage("invalid_amount"));
            return true;
        }

        Currency currency = PluginManager.getInstance().getCurrenciesManager().get(currencyCode);
        if (currency == null) {
            sender.sendMessage(MessagesUtils.getMessage("invalid_currency"));
            return true;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(MessagesUtils.getMessage("player_not_found"));
            return true;
        }

        // Calculate tax
        double taxRate = PluginManager.getInstance().getTaxRate();
        double taxAmount = amount * taxRate;
        double finalAmount = amount - taxAmount;

        // Process payment
        Player payer = (Player) sender;
        processPayment(payer, target, currency, amount, finalAmount, taxAmount);

        return true;
    }

    private void processPayment(Player from, Player to, Currency currency, double originalAmount,
                                double finalAmount, double taxAmount) {
        CompletableFuture<Optional<PlayerProfile>> fromProfileFuture =
                plugin.getStorageHandler().loadProfile(from.getUniqueId());
        CompletableFuture<Optional<PlayerProfile>> toProfileFuture =
                plugin.getStorageHandler().loadProfile(to.getUniqueId());

        CompletableFuture.allOf(fromProfileFuture, toProfileFuture)
                .thenAccept(v -> {
                    Optional<PlayerProfile> fromProfile = fromProfileFuture.join();
                    Optional<PlayerProfile> toProfile = toProfileFuture.join();

                    if (!fromProfile.isPresent() || !toProfile.isPresent()) {
                        from.sendMessage(MessagesUtils.getMessage("transaction_failed"));
                        return;
                    }

                    PlayerProfile payer = fromProfile.get();
                    PlayerProfile receiver = toProfile.get();

                    if (!payer.removeBalance(currency.getId(), originalAmount)) {
                        from.sendMessage(MessagesUtils.getMessage("insufficient_funds"));
                        return;
                    }

                    receiver.addBalance(currency.getId(), finalAmount);

                    // Save both profiles
                    CompletableFuture<Void> saveFrom = plugin.getStorageHandler().saveProfile(payer);
                    CompletableFuture<Void> saveTo = plugin.getStorageHandler().saveProfile(receiver);

                    CompletableFuture.allOf(saveFrom, saveTo)
                            .thenRun(() -> {
                                from.sendMessage(MessagesUtils.getMessage("payment_sent",
                                        "amount", currency.getSymbol() + String.format("%.2f", originalAmount),
                                        "target", to.getName(),
                                        "tax", String.format("%.2f", taxAmount)));

                                to.sendMessage(MessagesUtils.getMessage("payment_received",
                                        "amount", currency.getSymbol() + String.format("%.2f", finalAmount),
                                        "from", from.getName()));
                            });
                });
    }
}