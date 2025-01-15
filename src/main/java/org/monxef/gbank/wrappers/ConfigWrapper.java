package org.monxef.gbank.wrappers;


import org.monxef.gbank.enums.ConfigurationType;
import org.monxef.gbank.managers.PluginManager;
import org.monxef.gbank.objects.YamlConfigLoader;

public class ConfigWrapper {
    public static YamlConfigLoader valueOf(ConfigurationType type){
        return PluginManager.getInstance().getConfigurationManager().get(type);
    }
}