/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ch.njol.skript.variables.Variables
 *  net.kyori.adventure.text.Component
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.plugin.Plugin
 */
package ru.politaria.identity;

import ch.njol.skript.variables.Variables;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import ru.politaria.identity.PolitariaIdentityPlugin;

final class AuthTrustManager
implements Listener {
    private static final long TRUST_DURATION_MS = 604800000L;
    private static final String FINGERPRINT_SALT = "PolitariaIdentity/auth-trust/v1";
    private final PolitariaIdentityPlugin plugin;
    private final File file;
    private final YamlConfiguration config;

    AuthTrustManager(PolitariaIdentityPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auth-trust.yml");
        this.config = YamlConfiguration.loadConfiguration((File)this.file);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> this.tryAutoLogin(playerId), 2L);
    }

    void trust(Player player) {
        String fingerprint = this.fingerprint(player);
        if (fingerprint == null) {
            return;
        }
        String path = "players." + String.valueOf(player.getUniqueId());
        this.config.set(path + ".fingerprint", (Object)fingerprint);
        this.config.set(path + ".trusted-until", (Object)(System.currentTimeMillis() + 604800000L));
        this.save();
    }

    void forget(UUID playerId) {
        this.config.set("players." + String.valueOf(playerId), null);
        this.save();
    }

    void shutdown() {
        this.save();
    }

    private void tryAutoLogin(UUID playerId) {
        Player player = Bukkit.getPlayer((UUID)playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        if (Variables.getVariable((String)("auth.password::" + player.getName()), null, (boolean)false) == null) {
            return;
        }
        String path = "players." + String.valueOf(playerId);
        long trustedUntil = this.config.getLong(path + ".trusted-until", 0L);
        if (trustedUntil <= System.currentTimeMillis()) {
            if (this.config.contains(path)) {
                this.config.set(path, null);
                this.save();
            }
            return;
        }
        String savedFingerprint = this.config.getString(path + ".fingerprint");
        String currentFingerprint = this.fingerprint(player);
        if (savedFingerprint == null || !savedFingerprint.equals(currentFingerprint)) {
            return;
        }
        Variables.setVariable((String)("auth.logged-in::" + player.getName()), (Object)true, null, (boolean)false);
        Variables.deleteVariable((String)("auth.attempts::" + player.getName()), null, (boolean)false);
        Variables.deleteVariable((String)("auth.mode::" + player.getName()), null, (boolean)false);
        player.sendActionBar((Component)Component.empty());
        player.sendMessage(String.valueOf(ChatColor.GREEN) + "\u0410\u0432\u0442\u043e\u043c\u0430\u0442\u0438\u0447\u0435\u0441\u043a\u0438\u0439 \u0432\u0445\u043e\u0434 \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d: \u044d\u0442\u043e\u0442 IP \u0437\u0430\u043f\u043e\u043c\u043d\u0435\u043d \u043d\u0430 7 \u0434\u043d\u0435\u0439.");
    }

    private String fingerprint(Player player) {
        if (player.getAddress() == null) {
            return null;
        }
        InetAddress address = player.getAddress().getAddress();
        if (address == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = (address.getHostAddress() + ":PolitariaIdentity/auth-trust/v1").getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(value));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void save() {
        try {
            this.config.save(this.file);
        }
        catch (IOException exception) {
            this.plugin.getLogger().severe("Unable to save trusted login IPs: " + exception.getMessage());
        }
    }
}

