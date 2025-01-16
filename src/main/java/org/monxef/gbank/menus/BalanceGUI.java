package org.monxef.gbank.menus;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.monxef.gbank.GBank;
import org.monxef.gbank.enums.TransactionType;
import org.monxef.gbank.managers.PluginManager;
import org.monxef.gbank.objects.Currency;
import org.monxef.gbank.objects.PlayerProfile;
import org.monxef.gbank.objects.Transaction;
import org.monxef.gbank.utils.MessagesUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class BalanceGUI implements Listener {
    private static final int INVENTORY_SIZE = 54;
    private static final int UPDATE_INTERVAL_TICKS = 100;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd HH:mm");
    private static final int TRANSACTION_HISTORY_LIMIT = 5;

    private final GBank plugin;
    private final Player player;
    private final Inventory inventory;
    private final Map<Integer, Currency> slotCurrencyMap;
    private boolean isUpdateScheduled;

    private static final class GuiSlots {
        static final int REFRESH_BUTTON_SLOT = INVENTORY_SIZE - 5;
        static final int CLOSE_BUTTON_SLOT = INVENTORY_SIZE - 1;
    }

    public BalanceGUI(GBank plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.slotCurrencyMap = new HashMap<>();
        this.inventory = createInventory();

        registerListeners();
        initializeGUI();
    }

    private Inventory createInventory() {
        return Bukkit.createInventory(null, INVENTORY_SIZE, MessagesUtils.getMessage("balance-gui-title"));
    }

    private void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void initializeGUI() {
        loadBalances();
        schedulePeriodicUpdate();
    }

    private void schedulePeriodicUpdate() {
        if (isUpdateScheduled) return;

        isUpdateScheduled = true;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || inventory.getViewers().isEmpty()) {
                isUpdateScheduled = false;
                return;
            }
            loadBalances();
        }, UPDATE_INTERVAL_TICKS, UPDATE_INTERVAL_TICKS);
    }

    private void loadBalances() {
        plugin.getStorageHandler().loadProfile(player.getUniqueId())
                .thenAccept(this::updateGUIWithProfile)
                .exceptionally(this::handleLoadError);
    }

    private void updateGUIWithProfile(Optional<PlayerProfile> profileOpt) {
        if (!profileOpt.isPresent()) {
            plugin.getLogger().warning("Could not load profile for player: " + player.getName());
            return;
        }

        PlayerProfile profile = profileOpt.get();
        Map<Currency, Double> balances = getBalances(profile);

        Bukkit.getScheduler().runTask(plugin, () -> {
            updateCurrencySlots(balances);
            fillEmptySlots();
            addNavigationButtons();
        });
    }

    private Map<Currency, Double> getBalances(PlayerProfile profile) {
        Map<Currency, Double> balances = new HashMap<>();
        PluginManager.getInstance().getCurrenciesManager().getManager().values()
                .forEach(currency -> balances.put(currency, profile.getBalance(currency.getId())));
        return balances;
    }

    private void updateCurrencySlots(Map<Currency, Double> balances) {
        slotCurrencyMap.clear();

        for (Map.Entry<Currency, Double> entry : balances.entrySet()) {
            Currency currency = entry.getKey();
            double balance = entry.getValue();

            int slot = currency.getSlot();

            if (slot >= 0 && slot < INVENTORY_SIZE) {
                createCurrencyDisplay(currency, balance, slot);
                slotCurrencyMap.put(slot, currency);
            } else {
                plugin.getLogger().warning("Invalid slot configuration for currency " +
                        currency.getId() + ": " + slot + ". Must be between 0 and " + (INVENTORY_SIZE - 1));
            }
        }
    }

    private void createCurrencyDisplay(Currency currency, double balance, int slot) {
        ItemStack item = new ItemStack(currency.getDisplayMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName("§b" + currency.getDisplayName());
        List<String> lore = createCurrencyLore(currency, balance);

        loadTransactionHistory(currency, lore)
                .thenAccept(updatedLore -> {
                    meta.setLore(updatedLore);
                    item.setItemMeta(meta);
                    safelySetItem(slot, item);
                });
    }

    private List<String> createCurrencyLore(Currency currency, double balance) {
        List<String> lore = new ArrayList<>();
        String balanceLine = "§bYou have §a" + currency.getSymbol() + String.format("%.2f", balance);
        lore.add(balanceLine);
        return lore;
    }

    private CompletableFuture<List<String>> loadTransactionHistory(Currency currency, List<String> baseLore) {
        return plugin.getTransactionLogger()
                .getTransactions(player.getUniqueId(), currency.getId(), TRANSACTION_HISTORY_LIMIT)
                .thenApply(transactions -> addTransactionHistoryToLore(baseLore, transactions));
    }

    private List<String> addTransactionHistoryToLore(List<String> lore, List<Transaction> transactions) {
        if (transactions.isEmpty()) return lore;

        lore.add("");
        lore.add("§6Recent Transactions:");

        transactions.forEach(transaction -> {
            String timeStr = DATE_FORMAT.format(new Date(transaction.getTimestamp()));
            String amountStr = formatTransactionAmount(transaction.getAmount());
            
            lore.add(String.format("§7%s §8| %s §8| §7%s",
                formatTransactionType(transaction.getType()),
                amountStr,
                timeStr));
        });

        return lore;
    }

    private String formatTransactionAmount(double amount) {
        return (amount >= 0 ? "§a+" : "§c") + String.format("%.2f", Math.abs(amount));
    }

    private String formatTransactionType(TransactionType type) {
        return switch (type) {
            case DEPOSIT -> "§a↑ Deposit";
            case WITHDRAWAL -> "§c↓ Withdrawal";
            case TRANSFER_IN -> "§a← Received";
            case TRANSFER_OUT -> "§c→ Sent";
            case INTEREST -> "§6✦ Interest";
            case AUTOMATIC -> "§b⚡ Automatic";
            case ADMIN_SET -> "§b⚡ Admin Set";
            case ADMIN_RESET -> "§b⚡ Admin Reset";
            case ADMIN_TRANSFER -> "§b⚡ Admin Trans";
            case SYSTEM -> "§7⚙ System";
        };
    }

    private void fillEmptySlots() {
        ItemStack filler = createFillerItem();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    private ItemStack createFillerItem() {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }
        return filler;
    }

    private void addNavigationButtons() {
        inventory.setItem(GuiSlots.REFRESH_BUTTON_SLOT, createRefreshButton());
        inventory.setItem(GuiSlots.CLOSE_BUTTON_SLOT, createCloseButton());
    }

    private ItemStack createRefreshButton() {
        ItemStack button = new ItemStack(Material.CLOCK);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e↻ Refresh");
            meta.setLore(Arrays.asList(
                    "§7Click to refresh your balance",
                    "§7and transaction history"
            ));
            button.setItemMeta(meta);
        }
        return button;
    }

    private ItemStack createCloseButton() {
        ItemStack button = new ItemStack(Material.BARRIER);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c✕ Close");
            button.setItemMeta(meta);
        }
        return button;
    }

    private void safelySetItem(int slot, ItemStack item) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (inventory.getViewers().contains(player)) {
                inventory.setItem(slot, item);
            }
        });
    }

    private Void handleLoadError(Throwable error) {
        plugin.getLogger().log(Level.SEVERE, "Error loading balance GUI", error);
        return null;
    }

    public void open() {
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);

        if (event.getWhoClicked() != player) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        handleInventoryClick(event.getSlot());
    }

    private void handleInventoryClick(int slot) {
        if (slot == GuiSlots.REFRESH_BUTTON_SLOT) {
            loadBalances();
            player.playSound(player.getLocation(), "block.note_block.ping", 1.0f, 2.0f);
        } else if (slot == GuiSlots.CLOSE_BUTTON_SLOT) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory) && event.getPlayer().equals(player)) {
            cleanup();
        }
    }

    private void cleanup() {
        isUpdateScheduled = false;
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
    }
}