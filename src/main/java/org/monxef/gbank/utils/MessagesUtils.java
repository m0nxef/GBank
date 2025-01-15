package org.monxef.gbank.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.monxef.gbank.GBank;
import org.monxef.gbank.enums.ConfigurationType;
import org.monxef.gbank.wrappers.ConfigWrapper;

public class MessagesUtils {
    public static String format(String s){
        return ChatColor.translateAlternateColorCodes('&',s);
    }
    public static String getPrefix(){
        return GBank.getPlugin().getPrefix();
    }
    public static String getMessage(String key) {
        return format(ConfigWrapper.valueOf(ConfigurationType.MESSAGE).getConfig().getString(key, "Message not found: " + key));
    }
    public static String getMessage(String key, Object... args) {
        String message = ConfigWrapper.valueOf(ConfigurationType.MESSAGE).getConfig().getString(key, "Message not found: " + key);
        for (int i = 0; i < args.length; i += 2) {
            message = message.replace("{" + args[i] + "}", String.valueOf(args[i + 1]));
        }
        return format(message);
    }
    public static void send(String s){
        Bukkit.getConsoleSender().sendMessage(format(getMessage("prefix")+s));
    }
}
