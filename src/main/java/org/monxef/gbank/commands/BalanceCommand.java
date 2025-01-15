package org.monxef.gbank.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.monxef.gbank.GBank;
import org.monxef.gbank.menus.BalanceGUI;
import org.monxef.gbank.objects.PlayerProfile;
import org.monxef.gbank.utils.MessagesUtils;

import java.util.UUID;

public class BalanceCommand implements CommandExecutor {
    private final GBank plugin;

    public BalanceCommand(GBank plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player) && args.length < 1) {
            sender.sendMessage("Console must specify a player!");
            return true;
        }

        if (!sender.hasPermission("gbank.balance")) {
            sender.sendMessage(MessagesUtils.getMessage("no-permission"));
            return true;
        }

        // Handle command formats
        if (args.length == 0) {
            // Show own balance GUI
            showBalanceGUI((Player) sender);
            return true;
        }

        if (args.length == 1) {
            // Check other player's balance
            checkPlayerBalance(sender, args[0]);
            return true;
        }

        // Invalid usage
        sender.sendMessage(MessagesUtils.getMessage("balance-usage"));
        return true;
    }

    private void showBalanceGUI(Player player) {
        new BalanceGUI(plugin, player).open();
    }

    private void checkPlayerBalance(CommandSender sender, String targetPlayer) {
        Player target = Bukkit.getPlayer(targetPlayer);
        UUID targetId = target != null ? target.getUniqueId() : null;

        if (targetId == null) {
            // Try to load from offline player data
            try {
                targetId = Bukkit.getOfflinePlayer(targetPlayer).getUniqueId();
            } catch (Exception e) {
                sender.sendMessage(MessagesUtils.getMessage("player-not-found"));
                return;
            }
        }

        plugin.getStorageHandler().loadProfile(targetId)
                .thenAccept(optionalProfile -> {
                    if (!optionalProfile.isPresent()) {
                        sender.sendMessage(MessagesUtils.getMessage("no_profile"));
                        return;
                    }

                    PlayerProfile profile = optionalProfile.get();
                    // Show all currencies and their balances in the GUI
                    if (sender instanceof Player) {
                        new BalanceGUI(plugin, (Player) sender).open();
                    } else {
                        // For console, show text-based summary
                        sendBalanceSummary(sender, targetPlayer, profile);
                    }
                });
    }

    private void sendBalanceSummary(CommandSender sender, String targetPlayer, PlayerProfile profile) {
        // Implementation for showing text-based balance summary to console
        // This would iterate through all currencies and show their balances
        sender.sendMessage(MessagesUtils.getMessage("balance_summary_header", "player", targetPlayer));
        profile.getBalances().forEach((currency, amount) -> {
            sender.sendMessage(MessagesUtils.getMessage("balance_summary_line",
                    "currency", currency,
                    "amount", String.format("%.2f", amount)));
        });
    }
}