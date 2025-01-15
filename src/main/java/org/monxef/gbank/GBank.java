package org.monxef.gbank;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.monxef.gbank.commands.BalanceCommand;
import org.monxef.gbank.commands.PayCommand;
import org.monxef.gbank.enums.ConfigurationType;
import org.monxef.gbank.managers.PluginManager;
import org.monxef.gbank.objects.TransactionLogger;
import org.monxef.gbank.objects.YamlConfigLoader;
import org.monxef.gbank.storage.StorageHandler;
import org.monxef.gbank.storage.impl.JsonStorageHandler;
import org.monxef.gbank.storage.impl.MongoDBStorageHandler;
import org.monxef.gbank.storage.impl.MySQLStorageHandler;
import org.monxef.gbank.tasks.AutomaticPaymentTask;
import org.monxef.gbank.tasks.PluginLoadingTask;
import org.monxef.gbank.wrappers.ConfigWrapper;

import java.util.logging.Level;

public class GBank extends JavaPlugin {
    // API Methods
    @Getter
    private static GBank plugin;
    @Getter private final PluginManager pluginManager;

    @Getter
    TransactionLogger transactionLogger;
    @Getter
    private String prefix;

    @Getter
    private StorageHandler storageHandler;
    private FileConfiguration config;
    public GBank(){
        plugin = this;
        this.pluginManager = new PluginManager();
        pluginManager.register(
                new YamlConfigLoader(plugin, "config", ConfigurationType.DEFAULT),
                new YamlConfigLoader(plugin, "language", ConfigurationType.MESSAGE)
        );
    }
    @Override
    public void onEnable() {

        transactionLogger = new TransactionLogger(this);
        prefix = ConfigWrapper.valueOf(ConfigurationType.MESSAGE).getString("prefix");
        // Initialize configurations
        saveDefaultConfig();

        // Initialize storage
        initializeStorage();


        // Start automatic payment task
        startAutomaticPayments();

        new PluginLoadingTask().run();

        getLogger().info("GBank has been enabled!");
    }


    private void initializeStorage() {
        String storageType = getConfig().getString("storage.type", "JSON");

        try {
            switch (storageType.toUpperCase()) {
                case "JSON":
                    storageHandler = new JsonStorageHandler(this);
                    break;
                case "MONGODB":
                    String uri = getConfig().getString("storage.mongodb.uri");
                    storageHandler = new MongoDBStorageHandler(uri);
                    break;
                case "MYSQL":
                    String host = getConfig().getString("storage.mysql.host");
                    int port = getConfig().getInt("storage.mysql.port");
                    String database = getConfig().getString("storage.mysql.database");
                    String user = getConfig().getString("storage.mysql.user");
                    String password = getConfig().getString("storage.mysql.password");
                    Boolean ssl = getConfig().getBoolean("storage.mysql.ssl");
                    storageHandler = new MySQLStorageHandler(host, port, database, user, password,ssl);
                    break;
                default:
                    getLogger().warning("Invalid storage type! Defaulting to JSON storage.");
                    storageHandler = new JsonStorageHandler(this);
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize storage handler", e);
            getLogger().warning("Defaulting to JSON storage due to error.");
            storageHandler = new JsonStorageHandler(this);
        }
    }
    private void startAutomaticPayments() {
        long interval = getConfig().getLong("payment_interval", 60) * 20L; // Convert to ticks
        double amount = getConfig().getDouble("payment_amount", 10.0);

        new AutomaticPaymentTask(this, amount).runTaskTimer(this, interval, interval);
    }

}