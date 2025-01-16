package org.monxef.gbank.managers;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.monxef.gbank.GBank;
import org.monxef.gbank.enums.ConfigurationType;
import org.monxef.gbank.objects.Currency;
import org.monxef.gbank.objects.YamlConfigLoader;
import org.monxef.gbank.wrappers.ConfigWrapper;

import java.util.HashMap;
import java.util.Map;

public class CurrenciesManager {

    @Getter
    private Map<String, Currency> manager = new HashMap<>();
    @Getter @Setter
    private Currency defaultCurrency;
    public void loadAll() {
        YamlConfigLoader config = ConfigWrapper.valueOf(ConfigurationType.DEFAULT);
        if (config.getConfig().contains("currencies")) {
            for (String key : config.getConfig().getConfigurationSection("currencies").getKeys(false)) {
                String displayName = config.getString("currencies." + key + ".display-name");
                Material material = Material.getMaterial(config.getConfig().getString("currencies." + key + ".display-material","GOLD_INGOT"));
                String symbol = config.getString("currencies." + key + ".symbol");
                int slot = config.getConfig().getInt("currencies." + key + ".slot");
                register(key, new Currency(key, displayName, material, symbol, slot));
            }
        }
        GBank.getPlugin().getLogger().info("The plugin has loaded "+manager.size()+" currencies.");
    }
    public Currency get(String type) {
        if (getManager().containsKey(type)) {
            return getManager().get(type);
        }
        return null;
    }

    public void register(String key, Currency currency){
        if(getManager().containsValue(currency)) return;
        getManager().put(key, currency);
    }
    public boolean contains(ConfigurationType type){
        return getManager().containsKey(type);
    }
    public boolean contains(YamlConfigLoader configuration){
        return getManager().containsValue(configuration);
    }
}
