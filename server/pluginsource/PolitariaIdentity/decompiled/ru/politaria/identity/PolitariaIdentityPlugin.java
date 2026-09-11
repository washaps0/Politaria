/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ch.njol.skript.variables.Variables
 *  com.comphenix.protocol.PacketType$Play$Server
 *  com.comphenix.protocol.ProtocolLibrary
 *  com.comphenix.protocol.ProtocolManager
 *  com.comphenix.protocol.events.PacketContainer
 *  com.comphenix.protocol.wrappers.EnumWrappers$ChatFormatting
 *  com.comphenix.protocol.wrappers.EnumWrappers$TeamCollisionRule
 *  com.comphenix.protocol.wrappers.EnumWrappers$TeamVisibility
 *  com.comphenix.protocol.wrappers.WrappedChatComponent
 *  com.comphenix.protocol.wrappers.WrappedTeamParameters
 *  me.neznamy.tab.api.TabAPI
 *  me.neznamy.tab.api.event.EventHandler
 *  me.neznamy.tab.api.event.plugin.TabLoadEvent
 *  me.neznamy.tab.api.placeholder.PlaceholderManager
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.minimessage.MiniMessage
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Color
 *  org.bukkit.GameMode
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.ConsoleCommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.entity.Display$Billboard
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.entity.TextDisplay
 *  org.bukkit.entity.TextDisplay$TextAlignment
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerChangedWorldEvent
 *  org.bukkit.event.player.PlayerGameModeChangeEvent
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.event.player.PlayerRespawnEvent
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 *  org.bukkit.scheduler.BukkitTask
 *  org.bukkit.util.Transformation
 *  org.joml.AxisAngle4f
 *  org.joml.Vector3f
 */
package ru.politaria.identity;

import ch.njol.skript.variables.Variables;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedTeamParameters;
import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.event.EventHandler;
import me.neznamy.tab.api.event.plugin.TabLoadEvent;
import me.neznamy.tab.api.placeholder.PlaceholderManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import ru.politaria.identity.AuthTrustManager;
import ru.politaria.identity.TeleportManager;

public final class PolitariaIdentityPlugin
extends JavaPlugin
implements Listener {
    private static final String HIDDEN_TEAM = "pi_hidden";
    private static final float LABEL_PASSENGER_OFFSET = 0.95f;
    private static final ZoneId MOSCOW_TIME = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Set<String> ALLOWED_COLORS = Set.of("<white>", "<gray>", "<dark_gray>", "<black>", "<red>", "<gold>", "<yellow>", "<light_green>", "<dark_green>", "<dark_aqua>", "<aqua>", "<blue>", "<dark_purple>", "<light_purple>", "<#FF7EB6>");
    private final Map<UUID, Labels> labelsByPlayer = new HashMap<UUID, Labels>();
    private final Map<UUID, String> renderedLabelMarkup = new HashMap<UUID, String>();
    private final Set<String> knownRelations = new HashSet<String>();
    private final Map<UUID, String> playerCountries = new HashMap<UUID, String>();
    private ProtocolManager protocolManager;
    private File relationsFile;
    private YamlConfiguration relationsConfig;
    private BukkitTask labelTask;
    private BukkitTask tabPlaceholderTask;
    private EventHandler<TabLoadEvent> tabLoadHandler;
    private TeleportManager teleportManager;
    private AuthTrustManager authTrustManager;

    public void onEnable() {
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.loadRelations();
        Bukkit.getPluginManager().registerEvents((Listener)this, (Plugin)this);
        this.authTrustManager = new AuthTrustManager(this);
        Bukkit.getPluginManager().registerEvents((Listener)this.authTrustManager, (Plugin)this);
        this.teleportManager = new TeleportManager(this);
        Bukkit.getPluginManager().registerEvents((Listener)this.teleportManager, (Plugin)this);
        for (String commandName : Set.of("tpa", "tpaccept", "tpdeny", "capital")) {
            if (this.getCommand(commandName) == null) continue;
            this.getCommand(commandName).setExecutor((CommandExecutor)this.teleportManager);
            this.getCommand(commandName).setTabCompleter((TabCompleter)this.teleportManager);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.createLabels(player);
        }
        this.refreshEverything();
        this.tabPlaceholderTask = Bukkit.getScheduler().runTaskLater((Plugin)this, this::tryRegisterTabPlaceholders, 1L);
        this.tabLoadHandler = event -> Bukkit.getScheduler().runTaskLater((Plugin)this, this::tryRegisterTabPlaceholders, 1L);
        TabAPI.getInstance().getEventBus().register(TabLoadEvent.class, this.tabLoadHandler);
        this.labelTask = Bukkit.getScheduler().runTaskTimer((Plugin)this, this::maintainLabels, 20L, 20L);
        this.getLogger().info("\u041f\u0435\u0440\u0441\u043e\u043d\u0430\u043b\u044c\u043d\u044b\u0435 \u0438\u043c\u0435\u043d\u0430 \u0438\u0433\u0440\u043e\u043a\u043e\u0432 \u0432\u043a\u043b\u044e\u0447\u0435\u043d\u044b.");
    }

    private void registerTabPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("TAB") == null || Bukkit.getPluginManager().getPlugin("Skript") == null) {
            return;
        }
        PlaceholderManager placeholders = TabAPI.getInstance().getPlaceholderManager();
        this.unregisterTabPlaceholder(placeholders, "%politaria_balance%");
        this.unregisterTabPlaceholder(placeholders, "%politaria_country%");
        this.unregisterTabPlaceholder(placeholders, "%politaria_role%");
        this.unregisterTabPlaceholder(placeholders, "%politaria_members%");
        this.unregisterTabPlaceholder(placeholders, "%politaria_treasury%");
        this.unregisterTabPlaceholder(placeholders, "%politaria_daily_tax%");
        this.unregisterTabPlaceholder(placeholders, "%politaria_maxplayers%");
        this.unregisterTabPlaceholder(placeholders, "%politaria_time%");
        this.unregisterTabPlaceholder(placeholders, "%politaria_date%");
        placeholders.registerPlayerPlaceholder("%politaria_balance%", 1000, tabPlayer -> this.formatNumber(this.variable("economy.balance::" + String.valueOf(tabPlayer.getUniqueId())), "0"));
        placeholders.registerPlayerPlaceholder("%politaria_country%", 1000, tabPlayer -> this.countryName(tabPlayer.getUniqueId()));
        placeholders.registerPlayerPlaceholder("%politaria_role%", 1000, tabPlayer -> this.countryRole(tabPlayer.getUniqueId()));
        placeholders.registerPlayerPlaceholder("%politaria_members%", 1000, tabPlayer -> this.countryMembers(tabPlayer.getUniqueId()));
        placeholders.registerPlayerPlaceholder("%politaria_treasury%", 1000, tabPlayer -> this.countryTreasury(tabPlayer.getUniqueId()));
        placeholders.registerPlayerPlaceholder("%politaria_daily_tax%", 1000, tabPlayer -> this.countryDailyTax(tabPlayer.getUniqueId()));
        placeholders.registerServerPlaceholder("%politaria_maxplayers%", 5000, () -> Integer.toString(Bukkit.getMaxPlayers()));
        placeholders.registerServerPlaceholder("%politaria_time%", 1000, () -> TIME_FORMAT.format(ZonedDateTime.now(MOSCOW_TIME)));
        placeholders.registerServerPlaceholder("%politaria_date%", 60000, () -> DATE_FORMAT.format(ZonedDateTime.now(MOSCOW_TIME)));
        this.getLogger().info("\u041f\u043b\u0435\u0439\u0441\u0445\u043e\u043b\u0434\u0435\u0440\u044b TAB \u0434\u043b\u044f \u0441\u0442\u0440\u0430\u043d\u044b \u0438 \u0431\u0430\u043b\u0430\u043d\u0441\u0430 \u0432\u043a\u043b\u044e\u0447\u0435\u043d\u044b.");
    }

    private void tryRegisterTabPlaceholders() {
        try {
            this.registerTabPlaceholders();
        }
        catch (Exception exception) {
            this.getLogger().warning("TAB placeholders will be retried: " + exception.getMessage());
        }
    }

    private void unregisterTabPlaceholder(PlaceholderManager manager, String identifier) {
        if (manager.getPlaceholder(identifier) != null) {
            manager.unregisterPlaceholder(identifier);
        }
    }

    private Object variable(String name) {
        return Variables.getVariable((String)name, null, (boolean)false);
    }

    private String countryId(UUID playerId) {
        Object value = this.variable("country.player::" + String.valueOf(playerId));
        return value == null ? null : value.toString();
    }

    private String countryName(UUID playerId) {
        String countryId = this.countryId(playerId);
        if (countryId == null) {
            return "\u041d\u0435\u0442 \u0441\u0442\u0440\u0430\u043d\u044b";
        }
        Object name = this.variable("country.name::" + countryId);
        return name == null ? "\u041d\u0435\u0442 \u0441\u0442\u0440\u0430\u043d\u044b" : name.toString();
    }

    private String countryRole(UUID playerId) {
        String countryId = this.countryId(playerId);
        if (countryId == null) {
            return "\u041d\u0435\u0442 \u0440\u043e\u043b\u0438";
        }
        Object owner = this.variable("country.owner::" + countryId);
        if (owner != null && owner.toString().equals(playerId.toString())) {
            return "\u041f\u0440\u0430\u0432\u0438\u0442\u0435\u043b\u044c";
        }
        Object roleValue = this.variable("country.role-of::" + countryId + "::" + String.valueOf(playerId));
        String roleId = roleValue == null ? "citizen" : roleValue.toString();
        Object roleName = this.variable("country.role-name::" + countryId + "::" + roleId);
        return roleName == null ? "\u0413\u0440\u0430\u0436\u0434\u0430\u043d\u0438\u043d" : roleName.toString();
    }

    private String countryMembers(UUID playerId) {
        String countryId = this.countryId(playerId);
        if (countryId == null) {
            return "0";
        }
        int count = 0;
        Iterator iterator = Variables.getVariableIterator((String)("country.member::" + countryId + "::*"), (boolean)false, null);
        while (iterator.hasNext()) {
            iterator.next();
            ++count;
        }
        return Integer.toString(count);
    }

    private String countryTreasury(UUID playerId) {
        String countryId = this.countryId(playerId);
        if (countryId == null) {
            return "0";
        }
        return this.formatNumber(this.variable("country.treasury::" + countryId), "0");
    }

    private String countryDailyTax(UUID playerId) {
        String countryId = this.countryId(playerId);
        if (countryId == null) {
            return "0";
        }
        int claims = 0;
        Iterator iterator = Variables.getVariableIterator((String)("country.claims::" + countryId + "::*"), (boolean)false, null);
        while (iterator.hasNext()) {
            iterator.next();
            ++claims;
        }
        Object taxValue = this.variable("country.tax-per-chunk");
        if (!(taxValue instanceof Number)) {
            return "0";
        }
        Number taxPerChunk = (Number)taxValue;
        return this.formatNumber((double)claims * taxPerChunk.doubleValue(), "0");
    }

    private String formatNumber(Object value, String fallback) {
        Number number;
        double amount;
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number && (amount = (number = (Number)value).doubleValue()) == Math.rint(amount)) {
            return Long.toString(number.longValue());
        }
        return value.toString();
    }

    public void onDisable() {
        if (this.labelTask != null) {
            this.labelTask.cancel();
        }
        if (this.tabPlaceholderTask != null) {
            this.tabPlaceholderTask.cancel();
        }
        if (this.tabLoadHandler != null && Bukkit.getPluginManager().getPlugin("TAB") != null) {
            TabAPI.getInstance().getEventBus().unregister(this.tabLoadHandler);
        }
        if (this.teleportManager != null) {
            this.teleportManager.shutdown();
        }
        if (this.authTrustManager != null) {
            this.authTrustManager.shutdown();
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            this.sendRemoveHiddenTeam(viewer);
        }
        for (Labels labels : this.labelsByPlayer.values()) {
            labels.remove();
        }
        this.labelsByPlayer.clear();
        this.renderedLabelMarkup.clear();
        this.saveRelations();
    }

    @org.bukkit.event.EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {
            if (!event.getPlayer().isOnline()) {
                return;
            }
            this.createLabels(event.getPlayer());
            this.refreshEverything();
        }, 5L);
    }

    @org.bukkit.event.EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Labels labels = this.labelsByPlayer.remove(event.getPlayer().getUniqueId());
        this.renderedLabelMarkup.remove(event.getPlayer().getUniqueId());
        if (labels != null) {
            labels.remove();
        }
        Bukkit.getScheduler().runTask((Plugin)this, this::refreshEverything);
    }

    @org.bukkit.event.EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Bukkit.getScheduler().runTask((Plugin)this, () -> {
            if (event.getPlayer().isOnline()) {
                this.refreshTarget(event.getPlayer());
            }
        });
    }

    @org.bukkit.event.EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> this.recreateLabels(event.getPlayer()), 2L);
    }

    @org.bukkit.event.EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> this.recreateLabels(event.getPlayer()), 2L);
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("politaria.identity.manage")) {
            sender.sendMessage(String.valueOf(ChatColor.RED) + "\u042d\u0442\u0430 \u043a\u043e\u043c\u0430\u043d\u0434\u0430 \u0434\u043e\u0441\u0442\u0443\u043f\u043d\u0430 \u0442\u043e\u043b\u044c\u043a\u043e \u0441\u0435\u0440\u0432\u0435\u0440\u0443.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("auth-trust")) {
            try {
                Player player = Bukkit.getPlayer((UUID)UUID.fromString(args[1]));
                if (player != null && this.authTrustManager != null) {
                    this.authTrustManager.trust(player);
                }
            }
            catch (IllegalArgumentException ignored) {
                sender.sendMessage(String.valueOf(ChatColor.RED) + "\u0423\u043a\u0430\u0437\u0430\u043d \u043d\u0435\u0432\u0435\u0440\u043d\u044b\u0439 UUID \u0438\u0433\u0440\u043e\u043a\u0430.");
            }
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("auth-forget")) {
            try {
                if (this.authTrustManager != null) {
                    this.authTrustManager.forget(UUID.fromString(args[1]));
                }
            }
            catch (IllegalArgumentException ignored) {
                sender.sendMessage(String.valueOf(ChatColor.RED) + "\u0423\u043a\u0430\u0437\u0430\u043d \u043d\u0435\u0432\u0435\u0440\u043d\u044b\u0439 UUID \u0438\u0433\u0440\u043e\u043a\u0430.");
            }
            return true;
        }
        if (args.length != 3 || !args[0].equalsIgnoreCase("know") && !args[0].equalsIgnoreCase("forget") && !args[0].equalsIgnoreCase("country")) {
            sender.sendMessage("\u0418\u0441\u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u043d\u0438\u0435: /identity <know|forget|country> <UUID> <UUID|\u0441\u0442\u0440\u0430\u043d\u0430|none>");
            return true;
        }
        try {
            if (args[0].equalsIgnoreCase("country")) {
                UUID playerId = UUID.fromString(args[1]);
                if (args[2].equalsIgnoreCase("none")) {
                    this.playerCountries.remove(playerId);
                } else {
                    this.playerCountries.put(playerId, args[2]);
                }
                this.saveRelations();
                this.refreshEverything();
                return true;
            }
            UUID viewerId = UUID.fromString(args[1]);
            UUID targetId = UUID.fromString(args[2]);
            String key = this.relationKey(viewerId, targetId);
            if (args[0].equalsIgnoreCase("know")) {
                this.knownRelations.add(key);
            } else {
                this.knownRelations.remove(key);
            }
            this.saveRelations();
            Player viewer = Bukkit.getPlayer((UUID)viewerId);
            Player target = Bukkit.getPlayer((UUID)targetId);
            if (viewer != null && target != null) {
                this.updateLabelFor(viewer, target);
            }
            return true;
        }
        catch (IllegalArgumentException exception) {
            sender.sendMessage(String.valueOf(ChatColor.RED) + "\u0423\u043a\u0430\u0437\u0430\u043d \u043d\u0435\u0432\u0435\u0440\u043d\u044b\u0439 UUID \u0438\u0433\u0440\u043e\u043a\u0430.");
            return true;
        }
    }

    private String realLabelMarkup(Player target) {
        String roleColor;
        String roleName;
        String playerName = MINI_MESSAGE.escapeTags(target.getName());
        String countryId = this.playerCountries.get(target.getUniqueId());
        if (countryId == null) {
            return "<white>" + playerName;
        }
        Object countryNameValue = this.variable("country.name::" + countryId);
        if (countryNameValue == null) {
            return "<white>" + playerName;
        }
        String countryName = MINI_MESSAGE.escapeTags(countryNameValue.toString());
        String countryColor = this.labelColor(this.variable("country.color::" + countryId), "<white>");
        Object ownerValue = this.variable("country.owner::" + countryId);
        if (ownerValue != null && ownerValue.toString().equals(target.getUniqueId().toString())) {
            roleName = "\u041f\u0440\u0430\u0432\u0438\u0442\u0435\u043b\u044c";
            roleColor = "<gold>";
        } else {
            Object roleValue = this.variable("country.role-of::" + countryId + "::" + String.valueOf(target.getUniqueId()));
            String roleId = roleValue == null ? "citizen" : roleValue.toString();
            Object roleNameValue = this.variable("country.role-name::" + countryId + "::" + roleId);
            roleName = roleNameValue == null ? "\u0413\u0440\u0430\u0436\u0434\u0430\u043d\u0438\u043d" : roleNameValue.toString();
            roleName = MINI_MESSAGE.escapeTags(roleName);
            roleColor = this.labelColor(this.variable("country.role-color::" + countryId + "::" + roleId), "<white>");
        }
        return "<white>" + playerName + " <dark_gray>\u2022 " + countryColor + countryName + " <dark_gray>\u2022 " + roleColor + roleName;
    }

    private String labelColor(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String color = value.toString().replace(' ', '_');
        return ALLOWED_COLORS.contains(color) ? color : fallback;
    }

    private void createLabels(Player target) {
        Labels previous = this.labelsByPlayer.remove(target.getUniqueId());
        if (previous != null) {
            previous.remove();
        }
        String realMarkup = this.realLabelMarkup(target);
        TextDisplay real = this.createLabel(target, MINI_MESSAGE.deserialize((Object)realMarkup));
        TextDisplay unknown = this.createLabel(target, MINI_MESSAGE.deserialize((Object)"<gray>\u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u043e"));
        this.labelsByPlayer.put(target.getUniqueId(), new Labels(real, unknown));
        this.renderedLabelMarkup.put(target.getUniqueId(), realMarkup);
    }

    private TextDisplay createLabel(Player target, Component text) {
        TextDisplay display = (TextDisplay)target.getWorld().spawn(target.getLocation(), TextDisplay.class, entity -> {
            entity.text(text);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setBackgroundColor(Color.fromARGB((int)0, (int)0, (int)0, (int)0));
            entity.setShadowed(true);
            entity.setSeeThrough(false);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setLineWidth(300);
            entity.setViewRange(1.0f);
            entity.setDisplayWidth(4.0f);
            entity.setDisplayHeight(1.0f);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
            entity.setTransformation(new Transformation(new Vector3f(0.0f, 0.95f, 0.0f), new AxisAngle4f(), new Vector3f(1.0f, 1.0f, 1.0f), new AxisAngle4f()));
        });
        target.addPassenger((Entity)display);
        return display;
    }

    private void recreateLabels(Player target) {
        if (!target.isOnline()) {
            return;
        }
        this.createLabels(target);
        this.refreshTarget(target);
    }

    private void refreshEverything() {
        for (Player target : Bukkit.getOnlinePlayers()) {
            Labels labels = this.labelsByPlayer.get(target.getUniqueId());
            if (labels != null && labels.isValid()) continue;
            this.createLabels(target);
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            this.sendHiddenTeam(viewer);
            for (Player target : Bukkit.getOnlinePlayers()) {
                this.updateLabelFor(viewer, target);
            }
        }
    }

    private void maintainLabels() {
        for (Player target : Bukkit.getOnlinePlayers()) {
            Labels labels = this.labelsByPlayer.get(target.getUniqueId());
            if (labels == null || !labels.isValid() || labels.real().getVehicle() != target || labels.unknown().getVehicle() != target) {
                this.createLabels(target);
                this.refreshTarget(target);
                continue;
            }
            String markup = this.realLabelMarkup(target);
            if (markup.equals(this.renderedLabelMarkup.get(target.getUniqueId()))) continue;
            labels.real().text(MINI_MESSAGE.deserialize((Object)markup));
            this.renderedLabelMarkup.put(target.getUniqueId(), markup);
        }
    }

    private void refreshTarget(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            this.updateLabelFor(viewer, target);
        }
    }

    private void updateLabelFor(Player viewer, Player target) {
        Labels labels = this.labelsByPlayer.get(target.getUniqueId());
        if (labels == null || !labels.isValid()) {
            return;
        }
        viewer.hideEntity((Plugin)this, (Entity)labels.real());
        viewer.hideEntity((Plugin)this, (Entity)labels.unknown());
        if (target.getGameMode() == GameMode.SPECTATOR || viewer.getUniqueId().equals(target.getUniqueId())) {
            return;
        }
        if (this.knows(viewer.getUniqueId(), target.getUniqueId())) {
            viewer.showEntity((Plugin)this, (Entity)labels.real());
        } else {
            viewer.showEntity((Plugin)this, (Entity)labels.unknown());
        }
    }

    private void sendHiddenTeam(Player viewer) {
        this.sendRemoveHiddenTeam(viewer);
        try {
            PacketContainer packet = this.protocolManager.createPacket(PacketType.Play.Server.SCOREBOARD_TEAM);
            packet.getStrings().write(0, (Object)HIDDEN_TEAM);
            packet.getIntegers().write(0, (Object)0);
            WrappedTeamParameters parameters = WrappedTeamParameters.newBuilder().displayName(WrappedChatComponent.fromText((String)"")).prefix(WrappedChatComponent.fromText((String)"")).suffix(WrappedChatComponent.fromText((String)"")).nametagVisibility(EnumWrappers.TeamVisibility.NEVER).collisionRule(EnumWrappers.TeamCollisionRule.ALWAYS).color(EnumWrappers.ChatFormatting.RESET).options(0).build();
            packet.getOptionalTeamParameters().write(0, Optional.of(parameters));
            ArrayList<String> entries = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                entries.add(player.getName());
            }
            packet.getSpecificModifier(Collection.class).write(0, entries);
            this.protocolManager.sendServerPacket(viewer, packet);
        }
        catch (Exception exception) {
            this.getLogger().warning("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0441\u043a\u0440\u044b\u0442\u044c \u0441\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u044b\u0435 \u043d\u0438\u043a\u0438 \u0434\u043b\u044f " + viewer.getName() + ": " + exception.getMessage());
        }
    }

    private void sendRemoveHiddenTeam(Player viewer) {
        if (this.protocolManager == null) {
            return;
        }
        try {
            PacketContainer packet = this.protocolManager.createPacket(PacketType.Play.Server.SCOREBOARD_TEAM);
            packet.getStrings().write(0, (Object)HIDDEN_TEAM);
            packet.getIntegers().write(0, (Object)1);
            this.protocolManager.sendServerPacket(viewer, packet);
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private boolean knows(UUID viewerId, UUID targetId) {
        if (this.knownRelations.contains(this.relationKey(viewerId, targetId))) {
            return true;
        }
        String viewerCountry = this.playerCountries.get(viewerId);
        String targetCountry = this.playerCountries.get(targetId);
        return viewerCountry != null && viewerCountry.equals(targetCountry);
    }

    private String relationKey(UUID viewerId, UUID targetId) {
        return String.valueOf(viewerId) + "." + String.valueOf(targetId);
    }

    private void loadRelations() {
        if (!this.getDataFolder().exists() && !this.getDataFolder().mkdirs()) {
            this.getLogger().warning("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0441\u043e\u0437\u0434\u0430\u0442\u044c \u043f\u0430\u043f\u043a\u0443 \u0434\u0430\u043d\u043d\u044b\u0445 \u043f\u043b\u0430\u0433\u0438\u043d\u0430.");
        }
        this.relationsFile = new File(this.getDataFolder(), "known.yml");
        this.relationsConfig = YamlConfiguration.loadConfiguration((File)this.relationsFile);
        this.knownRelations.addAll(this.relationsConfig.getStringList("relations"));
        if (this.relationsConfig.getConfigurationSection("countries") != null) {
            for (String playerId : this.relationsConfig.getConfigurationSection("countries").getKeys(false)) {
                try {
                    this.playerCountries.put(UUID.fromString(playerId), this.relationsConfig.getString("countries." + playerId));
                }
                catch (IllegalArgumentException ignored) {
                    this.getLogger().warning("\u041f\u0440\u043e\u043f\u0443\u0449\u0435\u043d \u043d\u0435\u0432\u0435\u0440\u043d\u044b\u0439 UUID \u0441\u0442\u0440\u0430\u043d\u044b \u0432 known.yml: " + playerId);
                }
            }
        }
    }

    private void saveRelations() {
        if (this.relationsConfig == null || this.relationsFile == null) {
            return;
        }
        this.relationsConfig.set("relations", new ArrayList<String>(this.knownRelations));
        this.relationsConfig.set("countries", null);
        for (Map.Entry<UUID, String> entry : this.playerCountries.entrySet()) {
            this.relationsConfig.set("countries." + String.valueOf(entry.getKey()), (Object)entry.getValue());
        }
        try {
            this.relationsConfig.save(this.relationsFile);
        }
        catch (IOException exception) {
            this.getLogger().severe("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0441\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c \u0437\u043d\u0430\u043a\u043e\u043c\u0441\u0442\u0432\u0430: " + exception.getMessage());
        }
    }

    private record Labels(TextDisplay real, TextDisplay unknown) {
        private boolean isValid() {
            return this.real.isValid() && this.unknown.isValid();
        }

        private void remove() {
            this.real.remove();
            this.unknown.remove();
        }
    }
}

