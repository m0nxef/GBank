package org.monxef.gbank.managers;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.monxef.gbank.GBank;
import org.monxef.gbank.enums.ConfigurationType;
import org.monxef.gbank.objects.Currency;
import org.monxef.gbank.objects.YamlConfigLoader;
import org.monxef.gbank.wrappers.ConfigWrapper;

@Getter
public class PluginManager {
    @Getter
    private static PluginManager instance;
    private final ConfigurationManager configurationManager = new ConfigurationManager();
    private final CurrenciesManager currenciesManager = new CurrenciesManager();
    @Setter private int taxRate;
    public PluginManager(){
        instance = this;
    }
    public void register(Listener... listeners){
        for(Listener listener : listeners){
            Bukkit.getPluginManager().registerEvents(listener, GBank.getPlugin());
        }
    }
    public void register(YamlConfigLoader... configurations){
        for(YamlConfigLoader configuration : configurations){
            getConfigurationManager().register(configuration);
        }
    }
}
