package org.monxef.gbank.objects;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.monxef.gbank.enums.ConfigurationType;
import org.monxef.gbank.utils.MessagesUtils;

import java.io.File;
import java.util.List;

public class YamlConfigLoader {
    private final File file;
    private YamlConfiguration config;
    private ConfigurationType configurationType;

    public YamlConfigLoader(JavaPlugin plugin, String name, ConfigurationType configurationType) {
        this.configurationType = configurationType;
        this.file = new File(plugin.getDataFolder(), name+".yml");
        if (!this.file.exists()) {
            this.file.getParentFile().mkdir();
            plugin.saveResource(name+".yml", true);
        }
        this.config = new YamlConfiguration();
        try {
            this.config.load(this.file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public YamlConfigLoader(JavaPlugin plugin, String name, String path, ConfigurationType configurationType) {
        this.configurationType = configurationType;
        this.file = new File(plugin.getDataFolder()+"/"+path+"/", name+".yml");
        if (!this.file.exists()) {
            this.file.getParentFile().mkdir();
            plugin.saveResource(name+".yml", true);
        }
        this.config = new YamlConfiguration();
        try {
            this.config.load(this.file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void save() {
        try {
            this.config.save(this.file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public File getFile() {
        return this.file;
    }
    public YamlConfiguration getConfig() {
        return this.config;
    }
    public FileConfiguration getConfiguration() {
        return YamlConfiguration.loadConfiguration(file);
    }
    public ConfigurationType getConfigurationType() {
        return this.configurationType;
    }
    public String getString(String path) {
        return MessagesUtils.format(getConfiguration().getString(path));
    }
    public List<String> getStringList(String path) {
        return getConfiguration().getStringList(path);
    }
    public int getInt(String path) {
        return getConfiguration().getInt(path);
    }
    public long getLong(String path) {
        return getConfiguration().getLong(path);
    }
    public double getDouble(String path) {
        return getConfiguration().getDouble(path);
    }
    public boolean getBoolean(String path) {
        return getConfiguration().getBoolean(path);
    }
}