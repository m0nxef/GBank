package org.monxef.gbank.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.monxef.gbank.GBank;
import org.monxef.gbank.enums.TransactionType;
import org.monxef.gbank.managers.PluginManager;
import org.monxef.gbank.objects.PlayerProfile;
import org.monxef.gbank.objects.Currency;
import org.monxef.gbank.objects.Transaction;
import org.monxef.gbank.utils.MessagesUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class BankAdminCommand implements CommandExecutor, TabCompleter {
    private final GBank plugin;
    private final Set<String> validCommands = Set.of(
            "give", "take", "set", "transfer", "audit"
    );

    public BankAdminCommand(GBank plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("gbank.admin")) {
            sender.sendMessage(MessagesUtils.getMessage("no_permission"));
            return true;
        }

        if (args.length < 1 || !validCommands.contains(args[0].toLowerCase())) {
            sendHelpMessage(sender);
            return true;
        }

        try {
            return switch (args[0].toLowerCase()) {
                case "give" -> handleGiveCommand(sender, args);
                case "take" -> handleTakeCommand(sender, args);
                case "set" -> handleSetCommand(sender, args);
                case "transfer" -> handleTransferCommand(sender, args);
                case "audit" -> handleAuditCommand(sender, args);
                default -> {
                    sendHelpMessage(sender);
                    yield true;
                }
            };
        } catch (Exception e) {
            sender.sendMessage(MessagesUtils.getPrefix() + "§cAn error occurred while executing the command");
            plugin.getLogger().severe("Error executing command: " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }

    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage(MessagesUtils.getPrefix() + "§cUsage: /bank give <player> <amount> <currency>");
            return true;
        }

        String playerName = args[1];
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(MessagesUtils.getMessage("invalid_amount"));
            return true;
        }

        String currencyCode = args[3].toLowerCase();
        Currency currency = PluginManager.getInstance().getCurrenciesManager().get(currencyCode);
        if (currency == null) {
            sender.sendMessage(MessagesUtils.getPrefix() + "§cInvalid currency");
            return true;
        }

        CompletableFuture<Optional<PlayerProfile>> profileFuture = plugin.getStorageHandler()
                .loadProfile(targetPlayer.getUniqueId());

        profileFuture.thenAccept(optionalProfile -> {
            PlayerProfile profile = optionalProfile.orElseGet(() -> 
                    new PlayerProfile(targetPlayer.getUniqueId()));
            
            profile.addBalance(currencyCode, amount);

            plugin.getStorageHandler().saveProfile(profile)
                    .thenRun(() -> {
                        Transaction transaction = new Transaction(
                                TransactionType.DEPOSIT,
                                amount,
                                "Admin give command by " + sender.getName()
                        );

                        plugin.getTransactionLogger()
                                .logTransaction(targetPlayer.getUniqueId(), currencyCode, transaction)
                                .thenRun(() -> {
                                    sender.sendMessage(MessagesUtils.getMessage("admin.give_success")
                                            .replace("{prefix}", MessagesUtils.getPrefix())
                                            .replace("{player}", playerName)
                                            .replace("{amount}", String.format("%.2f", amount))
                                            .replace("{currency}", currency.getDisplayName()));

                                    Player target = Bukkit.getPlayer(targetPlayer.getUniqueId());
                                    if (target != null && target.isOnline()) {
                                        target.sendMessage(MessagesUtils.getMessage("player.received_admin_payment")
                                                .replace("{prefix}", MessagesUtils.getPrefix())
                                                .replace("{amount}", String.format("%.2f", amount))
                                                .replace("{currency}", currency.getDisplayName()));
                                    }
                                });
                    });
        }).exceptionally(e -> {
            sender.sendMessage(MessagesUtils.getMessage("command_error"));
            plugin.getLogger().severe("Error in give command: " + e.getMessage());
            return null;
        });

        return true;
    }

    private boolean handleTakeCommand(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage(MessagesUtils.getPrefix() + "§cUsage: /bank take <player> <amount> <currency>");
            return true;
        }

        String playerName = args[1];
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(MessagesUtils.getMessage("invalid_amount"));
            return true;
        }

        String currencyCode = args[3].toLowerCase();
        Currency currency = PluginManager.getInstance().getCurrenciesManager().get(currencyCode);
        if (currency == null) {
            sender.sendMessage(MessagesUtils.getPrefix() + "§cInvalid currency");
            return true;
        }

        plugin.getStorageHandler().loadProfile(targetPlayer.getUniqueId())
                .thenAccept(optionalProfile -> {
                    if (optionalProfile.isEmpty()) {
                        sender.sendMessage(MessagesUtils.getMessage("player_not_found"));
                        return;
                    }

                    PlayerProfile profile = optionalProfile.get();
                    if (!profile.hasBalance(currencyCode, amount)) {
                        sender.sendMessage(MessagesUtils.getMessage("insufficient_funds"));
                        return;
                    }

                    profile.removeBalance(currencyCode, amount);
                    plugin.getStorageHandler().saveProfile(profile)
                            .thenRun(() -> {
                                Transaction transaction = new Transaction(
                                        TransactionType.WITHDRAWAL,
                                        amount,
                                        "Admin take command by " + sender.getName()
                                );

                                plugin.getTransactionLogger()
                                        .logTransaction(targetPlayer.getUniqueId(), currencyCode, transaction)
                                        .thenRun(() -> {
                                            sender.sendMessage(MessagesUtils.getMessage("admin.take_success")
                                                    .replace("{prefix}", MessagesUtils.getPrefix())
                                                    .replace("{player}", playerName)
                                                    .replace("{amount}", String.format("%.2f", amount))
                                                    .replace("{currency}", currency.getDisplayName()));

                                            Player target = Bukkit.getPlayer(targetPlayer.getUniqueId());
                                            if (target != null && target.isOnline()) {
                                                target.sendMessage(MessagesUtils.getMessage("player.money_taken_by_admin")
                                                        .replace("{prefix}", MessagesUtils.getPrefix())
                                                        .replace("{amount}", String.format("%.2f", amount))
                                                        .replace("{currency}", currency.getDisplayName()));
                                            }
                                        });
                            });
                }).exceptionally(e -> {
                    sender.sendMessage(MessagesUtils.getMessage("command_error"));
                    plugin.getLogger().severe("Error in take command: " + e.getMessage());
                    return null;
                });

        return true;
    }

    private boolean handleSetCommand(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage(MessagesUtils.getPrefix() + "§cUsage: /bank set <player> <amount> <currency>");
            return true;
        }

        String playerName = args[1];
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
            if (amount < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(MessagesUtils.getMessage("invalid_amount"));
            return true;
        }

        String currencyCode = args[3].toLowerCase();
        Currency currency = PluginManager.getInstance().getCurrenciesManager().get(currencyCode);
        if (currency == null) {
            sender.sendMessage(MessagesUtils.getPrefix() + "§cInvalid currency");
            return true;
        }

        plugin.getStorageHandler().loadProfile(targetPlayer.getUniqueId())
                .thenAccept(optionalProfile -> {
                    PlayerProfile profile = optionalProfile.orElseGet(() -> 
                            new PlayerProfile(targetPlayer.getUniqueId()));
                    
                    profile.setBalance(currencyCode, amount);

                    plugin.getStorageHandler().saveProfile(profile)
                            .thenRun(() -> {
                                Transaction transaction = new Transaction(
                                        TransactionType.ADMIN_SET,
                                        amount,
                                        "Admin set command by " + sender.getName()
                                );

                                plugin.getTransactionLogger()
                                        .logTransaction(targetPlayer.getUniqueId(), currencyCode, transaction)
                                        .thenRun(() -> {
                                            sender.sendMessage(MessagesUtils.getMessage("admin.set_success")
                                                    .replace("{prefix}", MessagesUtils.getPrefix())
                                                    .replace("{player}", playerName)
                                                    .replace("{amount}", String.format("%.2f", amount))
                                                    .replace("{currency}", currency.getDisplayName()));

                                            Player target = Bukkit.getPlayer(targetPlayer.getUniqueId());
                                            if (target != null && target.isOnline()) {
                                                target.sendMessage(MessagesUtils.getMessage("player.balance_set_by_admin")
                                                        .replace("{prefix}", MessagesUtils.getPrefix())
                                                        .replace("{amount}", String.format("%.2f", amount))
                                                        .replace("{currency}", currency.getDisplayName()));
                                            }
                                        });
                            });
                }).exceptionally(e -> {
                    sender.sendMessage(MessagesUtils.getMessage("command_error"));
                    plugin.getLogger().severe("Error in set command: " + e.getMessage());
                    return null;
                });

        return true;
    }

    private boolean handleTransferCommand(CommandSender sender, String[] args) {
        if (args.length != 5) {
            sender.sendMessage(MessagesUtils.getPrefix() + "§cUsage: /bank transfer <from> <to> <amount> <currency>");
            return true;
        }

        String fromPlayerName = args[1];
        String toPlayerName = args[2];
        OfflinePlayer fromPlayer = Bukkit.getOfflinePlayer(fromPlayerName);
        OfflinePlayer toPlayer = Bukkit.getOfflinePlayer(toPlayerName);

        double amount;
        try {
            amount = Double.parseDouble(args[3]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(MessagesUtils.getMessage("invalid_amount"));
            return true;
        }

        String currencyCode = args[4].toLowerCase();
        Currency currency = PluginManager.getInstance().getCurrenciesManager().get(currencyCode);
        if (currency == null) {
            sender.sendMessage(MessagesUtils.getPrefix() + "§cInvalid currency");
            return true;
        }

        CompletableFuture<Optional<PlayerProfile>> fromProfileFuture = 
                plugin.getStorageHandler().loadProfile(fromPlayer.getUniqueId());
        CompletableFuture<Optional<PlayerProfile>> toProfileFuture = 
                plugin.getStorageHandler().loadProfile(toPlayer.getUniqueId());

        CompletableFuture.allOf(fromProfileFuture, toProfileFuture)
                .thenRun(() -> {
                    Optional<PlayerProfile> fromProfileOpt = fromProfileFuture.join();
                    Optional<PlayerProfile> toProfileOpt = toProfileFuture.join();

                    if (fromProfileOpt.isEmpty()) {
                        sender.sendMessage(MessagesUtils.getMessage("player_not_found"));
                        return;
                    }

                    PlayerProfile fromProfile = fromProfileOpt.get();
                    PlayerProfile toProfile = toProfileOpt.orElseGet(() -> 
                            new PlayerProfile(toPlayer.getUniqueId()));

                    if (!fromProfile.hasBalance(currencyCode, amount)) {
                        sender.sendMessage(MessagesUtils.getMessage("insufficient_funds"));
                        return;
                    }

                    fromProfile.removeBalance(currencyCode, amount);
                    toProfile.addBalance(currencyCode, amount);

                    CompletableFuture<Void> fromSave = plugin.getStorageHandler().saveProfile(fromProfile);
                    CompletableFuture<Void> toSave = plugin.getStorageHandler().saveProfile(toProfile);

                    CompletableFuture.allOf(fromSave, toSave)
                            .thenRun(() -> {
                                Transaction transaction = new Transaction(
                                        TransactionType.ADMIN_TRANSFER,
                                        amount,
                                        "Admin transfer from " + fromPlayerName + " to " + toPlayerName
                                );

                                CompletableFuture<Void> fromLog = plugin.getTransactionLogger()
                                        .logTransaction(fromPlayer.getUniqueId(), currencyCode, transaction);
                                CompletableFuture<Void> toLog = plugin.getTransactionLogger()
                                        .logTransaction(toPlayer.getUniqueId(), currencyCode, transaction);

                                CompletableFuture.allOf(fromLog, toLog)
                                        .thenRun(() -> {
                                            sender.sendMessage(MessagesUtils.getMessage("admin.transfer_success")
                                                    .replace("{prefix}", MessagesUtils.getPrefix())
                                                    .replace("{amount}", String.format("%.2f", amount))
                                                    .replace("{currency}", currency.getDisplayName())
                                                    .replace("{from}", fromPlayerName)
                                                    .replace("{to}", toPlayerName));

                                            notifyPlayers(fromPlayer, toPlayer, amount, currency);
                                        });
                            });
                }).exceptionally(e -> {
                    sender.sendMessage(MessagesUtils.getMessage("command_error"));
                    plugin.getLogger().severe("Error in transfer command: " + e.getMessage());
                    return null;
                });

        return true;
    }

    private void notifyPlayers(OfflinePlayer fromPlayer, OfflinePlayer toPlayer, 
            double amount, Currency currency) {
        Player fromPlayerOnline = Bukkit.getPlayer(fromPlayer.getUniqueId());
        if (fromPlayerOnline != null && fromPlayerOnline.isOnline()) {
            fromPlayerOnline.sendMessage(MessagesUtils.getMessage("player.money_transferred_from")
                    .replace("{prefix}", MessagesUtils.getPrefix())
                    .replace("{amount}", String.format("%.2f", amount))
                    .replace("{currency}", currency.getDisplayName())
                    .replace("{player}", toPlayer.getName()));
        }

        Player toPlayerOnline = Bukkit.getPlayer(toPlayer.getUniqueId());
        if (toPlayerOnline != null && toPlayerOnline.isOnline()) {
            toPlayerOnline.sendMessage(MessagesUtils.getMessage("player.money_transferred_to")
                    .replace("{prefix}", MessagesUtils.getPrefix())
                    .replace("{amount}", String.format("%.2f", amount))
                    .replace("{currency}", currency.getDisplayName())
                    .replace("{player}", fromPlayer.getName()));
        }
    }

    private boolean handleAuditCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessagesUtils.getPrefix() + "§cUsage: /bank audit <player> [limit] [currency]");
            return true;
        }

        String playerName = args[1];
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);

        int limit = 10;
        if (args.length > 2) {
            try {
                limit = Integer.parseInt(args[2]);
                if (limit <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                sender.sendMessage(MessagesUtils.getPrefix() + "§cInvalid number");
                return true;
            }
        }

        String currencyCode = PluginManager.getInstance().getCurrenciesManager().getDefaultCurrency().getId();
        if (args.length > 3) {
            Currency currency = PluginManager.getInstance().getCurrenciesManager().get(args[3]);
            if (currency == null) {
                sender.sendMessage(MessagesUtils.getPrefix() + "§cInvalid currency");
                return true;
            }
            currencyCode = currency.getId();
        }

        final String finalCurrencyCode = currencyCode;
        plugin.getTransactionLogger()
                .getTransactions(targetPlayer.getUniqueId(), currencyCode, limit)
                .thenAccept(transactions -> {
                    if (transactions.isEmpty()) {
                        sender.sendMessage(MessagesUtils.getPrefix() + "§cNo transactions found");
                        return;
                    }

                    sender.sendMessage(MessagesUtils.getPrefix() + "§6=== Transaction History for " + playerName + " ===");
                    
                    for (Transaction transaction : transactions) {
                        sender.sendMessage(String.format(
                            "%s§e%s: %.2f %s - %s - %s",
                            MessagesUtils.getPrefix(),
                            transaction.getType(),
                            transaction.getAmount(),
                            PluginManager.getInstance().getCurrenciesManager().get(finalCurrencyCode).getDisplayName(),
                            new Date(transaction.getTimestamp()).toString(),
                            transaction.getDetails()
                        ));
                    }
                }).exceptionally(e -> {
                    sender.sendMessage(MessagesUtils.getPrefix() + "§cAn error occurred while fetching transactions");
                    plugin.getLogger().severe("Error in audit command: " + e.getMessage());
                    return null;
                });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("gbank.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return List.of("give", "take", "set", "transfer", "audit")
                    .stream()
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return null; // Return null to show online players
        }

        // For transfer command, show players for both 'from' and 'to' arguments
        if (args[0].equalsIgnoreCase("transfer")) {
            if (args.length == 3) {
                return null; // Return null to show online players for 'to' player
            }
            if (args.length == 4) {
                return Collections.emptyList(); // Return empty list for amount argument
            }
            if (args.length == 5) {
                return PluginManager.getInstance().getCurrenciesManager().getManager().keySet().stream()
                        .filter(currency -> currency.startsWith(args[4].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        // For other commands (give, take, set)
        if (args.length == 3) {
            return Collections.emptyList(); // Return empty list for amount argument
        }
        if (args.length == 4 && List.of("give", "take", "set")
                .contains(args[0].toLowerCase())) {
            return PluginManager.getInstance().getCurrenciesManager().getManager().keySet().stream()
                    .filter(currency -> currency.startsWith(args[3].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // For audit command
        if (args[0].equalsIgnoreCase("audit") && args.length == 3) {
            return PluginManager.getInstance().getCurrenciesManager().getManager().keySet().stream()
                    .filter(currency -> currency.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(MessagesUtils.getPrefix() + "§6=== Bank Admin Commands ===");
        sender.sendMessage(MessagesUtils.getPrefix() + "§e/bank give <player> <amount> <currency> §7- Give money to a player");
        sender.sendMessage(MessagesUtils.getPrefix() + "§e/bank take <player> <amount> <currency> §7- Take money from a player");
        sender.sendMessage(MessagesUtils.getPrefix() + "§e/bank set <player> <amount> <currency> §7- Set player's balance");
        sender.sendMessage(MessagesUtils.getPrefix() + "§e/bank transfer <from> <to> <amount> <currency> §7- Transfer money between players");
        sender.sendMessage(MessagesUtils.getPrefix() + "§e/bank audit <player> [limit] [currency] §7- View transaction history");
        sender.sendMessage(MessagesUtils.getPrefix() + "§6==========================");
    }
}