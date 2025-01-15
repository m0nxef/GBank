package org.monxef.gbank.managers;
import com.google.common.collect.Maps;
import lombok.Getter;
import org.monxef.gbank.GBank;
import org.monxef.gbank.enums.ConfigurationType;
import org.monxef.gbank.objects.YamlConfigLoader;

import java.util.Map;

public class ConfigurationManager
{
    @Getter private final Map<ConfigurationType, YamlConfigLoader> manager = Maps.newHashMap();

    public YamlConfigLoader get(ConfigurationType type) {
        if (getManager().containsKey(type)) {
            return getManager().get(type);
        }
        GBank.getPlugin().getLogger().warning("Failed to load configuration type " + type);
        return null;
    }

    public YamlConfigLoader register(YamlConfigLoader configuration){
        if(getManager().containsKey(configuration.getConfigurationType())) return null;
        return getManager().put(configuration.getConfigurationType(),configuration);
    }
    public boolean contains(ConfigurationType type){
        return getManager().containsKey(type);
    }
    public boolean contains(YamlConfigLoader configuration){
        return getManager().containsValue(configuration);
    }

}