package ru.politaria.skinheads;

import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skinsrestorer.api.PropertyUtils;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.SkinProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

public final class PolitariaSkinHeadsPlugin extends JavaPlugin implements Listener {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private SkinsRestorer skinsRestorer;

    @Override
    public void onEnable() {
        Plugin sr = Bukkit.getPluginManager().getPlugin("SkinsRestorer");
        if (sr == null || !sr.isEnabled()) {
            getLogger().warning("SkinsRestorer is not available. GUI skin heads will stay vanilla.");
            return;
        }

        try {
            skinsRestorer = SkinsRestorerProvider.get();
            Bukkit.getPluginManager().registerEvents(this, this);
            getLogger().info("SkinsRestorer bridge enabled for Politaria GUI heads.");
        } catch (Throwable throwable) {
            getLogger().severe("Could not hook into SkinsRestorer: " + throwable.getMessage());
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (skinsRestorer == null) {
            return;
        }

        HumanEntity viewer = event.getPlayer();
        if (!(viewer instanceof Player)) {
            return;
        }

        String title = PLAIN.serialize(event.getView().title());
        if (!isSupportedMenu(title)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        for (int slot = 0; slot < top.getSize(); slot++) {
            ItemStack item = top.getItem(slot);
            if (item == null || item.getType() != Material.PLAYER_HEAD) {
                continue;
            }

            ItemMeta meta = item.getItemMeta();
            if (meta == null || meta.displayName() == null) {
                continue;
            }

            String playerName = cleanPlayerName(PLAIN.serialize(meta.displayName()));
            if (!looksLikePlayerName(playerName)) {
                continue;
            }

            OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
            UUID playerId = offline.getUniqueId();
            int targetSlot = slot;
            String expectedName = playerName;

            Bukkit.getScheduler().runTaskAsynchronously(this, () -> loadAndApply(top, targetSlot, playerId, expectedName));
        }
    }

    private void loadAndApply(Inventory inventory, int slot, UUID playerId, String playerName) {
        try {
            Optional<SkinProperty> skin = skinsRestorer.getPlayerStorage().getSkinForPlayer(playerId, playerName);
            if (skin.isEmpty()) {
                return;
            }

            String textureUrl = PropertyUtils.getSkinTextureUrl(skin.get());
            if (textureUrl == null || textureUrl.isBlank()) {
                return;
            }

            Bukkit.getScheduler().runTask(this, () -> applyTexture(inventory, slot, playerId, playerName, textureUrl));
        } catch (Throwable throwable) {
            getLogger().fine("Could not load SkinsRestorer skin for " + playerName + ": " + throwable.getMessage());
        }
    }

    private void applyTexture(Inventory inventory, int slot, UUID playerId, String playerName, String textureUrl) {
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.getType() != Material.PLAYER_HEAD) {
            return;
        }

        ItemMeta itemMeta = current.getItemMeta();
        if (!(itemMeta instanceof SkullMeta skullMeta) || itemMeta.displayName() == null) {
            return;
        }

        String currentName = cleanPlayerName(PLAIN.serialize(itemMeta.displayName()));
        if (!currentName.equalsIgnoreCase(playerName)) {
            return;
        }

        try {
            URL url = URI.create(textureUrl).toURL();
            PlayerProfile profile = Bukkit.createPlayerProfile(playerId, playerName);
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(url);
            profile.setTextures(textures);
            skullMeta.setOwnerProfile(profile);
            current.setItemMeta(skullMeta);
            inventory.setItem(slot, current);
        } catch (Throwable throwable) {
            getLogger().fine("Could not apply skin texture to head for " + playerName + ": " + throwable.getMessage());
        }
    }

    private boolean isSupportedMenu(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        return lower.equals("участники страны")
                || lower.equals("управление участником")
                || lower.equals("розыск страны")
                || lower.startsWith("розыск •")
                || lower.equals("паспорт игрока");
    }

    private String cleanPlayerName(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private boolean looksLikePlayerName(String name) {
        if (name.isBlank() || name.length() > 16) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                return false;
            }
        }
        return true;
    }
}
