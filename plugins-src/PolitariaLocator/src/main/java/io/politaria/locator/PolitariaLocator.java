package io.politaria.locator;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class PolitariaLocator extends JavaPlugin implements CommandExecutor {

    private CountryResolver countryResolver;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            this.countryResolver = new CountryResolver();
        } catch (ReflectiveOperationException ex) {
            getLogger().severe("Не удалось подключиться к Skript Variables API. PolitariaLocator отключён.");
            ex.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        new LocatorPacketListener(this, countryResolver).register();

        if (getCommand("politarialocator") != null) {
            getCommand("politarialocator").setExecutor(this);
        }

        if (getConfig().getBoolean("force-enable-locator-bar", true)) {
            Bukkit.getScheduler().runTask(this, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule minecraft:locator_bar true"));
        }

        getLogger().info("Politaria Locator включён: на Locator Bar видны только граждане своей страны.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("politaria.locator.admin")) {
            sender.sendMessage("§cНедостаточно прав.");
            return true;
        }

        reloadConfig();
        sender.sendMessage("§b§lPOLITARIA §8» §fКонфигурация Locator Bar перезагружена.");
        return true;
    }
}
