package org.monxef.gbank.tasks;

import org.monxef.gbank.GBank;
import org.monxef.gbank.commands.BalanceCommand;
import org.monxef.gbank.commands.BankAdminCommand;
import org.monxef.gbank.commands.PayCommand;
import org.monxef.gbank.enums.ConfigurationType;
import org.monxef.gbank.managers.PluginManager;
import org.monxef.gbank.objects.YamlConfigLoader;
import org.monxef.gbank.utils.MessagesUtils;
import org.monxef.gbank.wrappers.ConfigWrapper;

public class PluginLoadingTask extends Thread {

    @Override
    public void run() {
        YamlConfigLoader defaultConfig = ConfigWrapper.valueOf(ConfigurationType.DEFAULT);
        MessagesUtils.send("&eRegistering Commands....");
        GBank.getPlugin().getCommand("balance").setExecutor(new BalanceCommand(GBank.getPlugin()));
        GBank.getPlugin().getCommand("bank").setExecutor(new BankAdminCommand(GBank.getPlugin()));
        GBank.getPlugin().getCommand("pay").setExecutor(new PayCommand(GBank.getPlugin()));
        PluginManager.getInstance().getCurrenciesManager().loadAll();
        PluginManager.getInstance().setTaxRate(ConfigWrapper.valueOf(ConfigurationType.DEFAULT).getInt("payments.tax-rate"));
        PluginManager.getInstance().getCurrenciesManager().setDefaultCurrency(PluginManager.getInstance().getCurrenciesManager().get(ConfigWrapper.valueOf(ConfigurationType.DEFAULT).getString("default-currency")));

    }

}
