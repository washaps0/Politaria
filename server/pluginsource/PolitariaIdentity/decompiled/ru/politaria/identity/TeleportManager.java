/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ch.njol.skript.variables.Variables
 *  ch.njol.util.Pair
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.event.ClickEvent
 *  net.kyori.adventure.text.event.HoverEvent
 *  net.kyori.adventure.text.event.HoverEventSource
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Location
 *  org.bukkit.boss.BarColor
 *  org.bukkit.boss.BarFlag
 *  org.bukkit.boss.BarStyle
 *  org.bukkit.boss.BossBar
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.entity.EntityDamageEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitTask
 */
package ru.politaria.identity;

import ch.njol.skript.variables.Variables;
import ch.njol.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import ru.politaria.identity.PolitariaIdentityPlugin;
import ru.politaria.identity.VanillaTeleport;

final class TeleportManager
implements Listener,
CommandExecutor,
TabCompleter {
    private static final long REQUEST_LIFETIME_MS = 60000L;
    private static final int TELEPORT_DELAY_TICKS = 100;
    private static final int UPDATE_INTERVAL_TICKS = 2;
    private final PolitariaIdentityPlugin plugin;
    private final Map<UUID, LinkedHashMap<UUID, Long>> incomingRequests = new HashMap<UUID, LinkedHashMap<UUID, Long>>();
    private final Map<UUID, ActiveTeleport> activeTeleports = new HashMap<UUID, ActiveTeleport>();

    TeleportManager(PolitariaIdentityPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName;
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u042d\u0442\u0430 \u043a\u043e\u043c\u0430\u043d\u0434\u0430 \u0434\u043e\u0441\u0442\u0443\u043f\u043d\u0430 \u0442\u043e\u043b\u044c\u043a\u043e \u0438\u0433\u0440\u043e\u043a\u0430\u043c.");
            return true;
        }
        Player player = (Player)sender;
        if (!this.isLoggedIn(player)) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u0432\u043e\u0439\u0434\u0438\u0442\u0435 \u0432 \u0430\u043a\u043a\u0430\u0443\u043d\u0442.");
            return true;
        }
        return switch (commandName = command.getName().toLowerCase(Locale.ROOT)) {
            case "tpa" -> this.requestTeleport(player, args);
            case "tpaccept" -> this.answerRequest(player, args, true);
            case "tpdeny" -> this.answerRequest(player, args, false);
            case "capital" -> this.teleportToCapital(player, args);
            default -> false;
        };
    }

    private boolean requestTeleport(Player requester, String[] args) {
        if (args.length != 1) {
            requester.sendMessage(String.valueOf(ChatColor.YELLOW) + "\u0418\u0441\u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u043d\u0438\u0435: /tpa <\u043d\u0438\u043a>");
            return true;
        }
        Player target = Bukkit.getPlayerExact((String)args[0]);
        if (target == null || !target.isOnline()) {
            requester.sendMessage(String.valueOf(ChatColor.RED) + "\u042d\u0442\u043e\u0442 \u0438\u0433\u0440\u043e\u043a \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d \u0438\u043b\u0438 \u043d\u0435 \u0432 \u0441\u0435\u0442\u0438.");
            return true;
        }
        if (target.getUniqueId().equals(requester.getUniqueId())) {
            requester.sendMessage(String.valueOf(ChatColor.RED) + "\u041d\u0435\u043b\u044c\u0437\u044f \u043e\u0442\u043f\u0440\u0430\u0432\u0438\u0442\u044c \u0437\u0430\u043f\u0440\u043e\u0441 \u0441\u0430\u043c\u043e\u043c\u0443 \u0441\u0435\u0431\u0435.");
            return true;
        }
        if (!this.isLoggedIn(target)) {
            requester.sendMessage(String.valueOf(ChatColor.RED) + "\u042d\u0442\u043e\u0442 \u0438\u0433\u0440\u043e\u043a \u0435\u0449\u0451 \u043d\u0435 \u0432\u043e\u0448\u0451\u043b \u0432 \u0430\u043a\u043a\u0430\u0443\u043d\u0442.");
            return true;
        }
        this.cleanupExpiredRequests(target.getUniqueId());
        LinkedHashMap requests = this.incomingRequests.computeIfAbsent(target.getUniqueId(), ignored -> new LinkedHashMap());
        requests.put(requester.getUniqueId(), System.currentTimeMillis() + 60000L);
        requester.sendMessage(String.valueOf(ChatColor.GREEN) + "\u0417\u0430\u043f\u0440\u043e\u0441 \u043d\u0430 \u0442\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0430\u0446\u0438\u044e \u043e\u0442\u043f\u0440\u0430\u0432\u043b\u0435\u043d \u0438\u0433\u0440\u043e\u043a\u0443 " + target.getName() + ".");
        Component message = ((TextComponent)((TextComponent)Component.text((String)("\u0417\u0430\u043f\u0440\u043e\u0441 \u043d\u0430 \u0442\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0430\u0446\u0438\u044e \u043e\u0442 " + requester.getName() + " ")).append(((TextComponent)((TextComponent)Component.text((String)"[\u041f\u0420\u0418\u041d\u042f\u0422\u042c]").color((TextColor)NamedTextColor.GREEN)).clickEvent(ClickEvent.runCommand((String)("/tpaccept " + requester.getName())))).hoverEvent((HoverEventSource)HoverEvent.showText((Component)Component.text((String)"\u041f\u0440\u0438\u043d\u044f\u0442\u044c \u0437\u0430\u043f\u0440\u043e\u0441"))))).append((Component)Component.text((String)" "))).append(((TextComponent)((TextComponent)Component.text((String)"[\u041e\u0422\u041a\u041b\u041e\u041d\u0418\u0422\u042c]").color((TextColor)NamedTextColor.RED)).clickEvent(ClickEvent.runCommand((String)("/tpdeny " + requester.getName())))).hoverEvent((HoverEventSource)HoverEvent.showText((Component)Component.text((String)"\u041e\u0442\u043a\u043b\u043e\u043d\u0438\u0442\u044c \u0437\u0430\u043f\u0440\u043e\u0441"))));
        target.sendMessage(message);
        target.sendMessage(String.valueOf(ChatColor.GRAY) + "\u0417\u0430\u043f\u0440\u043e\u0441 \u0438\u0441\u0442\u0435\u0447\u0451\u0442 \u0447\u0435\u0440\u0435\u0437 60 \u0441\u0435\u043a\u0443\u043d\u0434.");
        return true;
    }

    private boolean answerRequest(Player target, String[] args, boolean accept) {
        Player requester;
        this.cleanupExpiredRequests(target.getUniqueId());
        LinkedHashMap<UUID, Long> requests = this.incomingRequests.get(target.getUniqueId());
        if (requests == null || requests.isEmpty()) {
            target.sendMessage(String.valueOf(ChatColor.RED) + "\u0423 \u0432\u0430\u0441 \u043d\u0435\u0442 \u0430\u043a\u0442\u0438\u0432\u043d\u044b\u0445 \u0437\u0430\u043f\u0440\u043e\u0441\u043e\u0432 \u043d\u0430 \u0442\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0430\u0446\u0438\u044e.");
            return true;
        }
        UUID requesterId = null;
        if (args.length > 0) {
            Player named = Bukkit.getPlayerExact((String)args[0]);
            if (named != null && requests.containsKey(named.getUniqueId())) {
                requesterId = named.getUniqueId();
            }
        } else if (requests.size() == 1) {
            requesterId = requests.keySet().iterator().next();
        }
        if (requesterId == null) {
            target.sendMessage(String.valueOf(ChatColor.YELLOW) + "\u0423\u043a\u0430\u0436\u0438\u0442\u0435 \u0438\u0433\u0440\u043e\u043a\u0430: /" + (accept ? "tpaccept" : "tpdeny") + " <\u043d\u0438\u043a>");
            return true;
        }
        requests.remove(requesterId);
        if (requests.isEmpty()) {
            this.incomingRequests.remove(target.getUniqueId());
        }
        if ((requester = Bukkit.getPlayer((UUID)requesterId)) == null || !requester.isOnline()) {
            target.sendMessage(String.valueOf(ChatColor.RED) + "\u0418\u0433\u0440\u043e\u043a \u0443\u0436\u0435 \u0432\u044b\u0448\u0435\u043b \u0441 \u0441\u0435\u0440\u0432\u0435\u0440\u0430.");
            return true;
        }
        if (!accept) {
            requester.sendMessage(String.valueOf(ChatColor.RED) + target.getName() + " \u043e\u0442\u043a\u043b\u043e\u043d\u0438\u043b \u0432\u0430\u0448 \u0437\u0430\u043f\u0440\u043e\u0441 \u043d\u0430 \u0442\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0430\u0446\u0438\u044e.");
            target.sendMessage(String.valueOf(ChatColor.GRAY) + "\u0417\u0430\u043f\u0440\u043e\u0441 \u0438\u0433\u0440\u043e\u043a\u0430 " + requester.getName() + " \u043e\u0442\u043a\u043b\u043e\u043d\u0451\u043d.");
            return true;
        }
        target.sendMessage(String.valueOf(ChatColor.GREEN) + "\u0412\u044b \u043f\u0440\u0438\u043d\u044f\u043b\u0438 \u0437\u0430\u043f\u0440\u043e\u0441 \u0438\u0433\u0440\u043e\u043a\u0430 " + requester.getName() + ".");
        requester.sendMessage(String.valueOf(ChatColor.GREEN) + "\u0417\u0430\u043f\u0440\u043e\u0441 \u043f\u0440\u0438\u043d\u044f\u0442. \u0422\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0430\u0446\u0438\u044f \u0447\u0435\u0440\u0435\u0437 5 \u0441\u0435\u043a\u0443\u043d\u0434.");
        this.startTeleport(requester, target.getUniqueId(), null, "\u0422\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0430\u0446\u0438\u044f \u043a " + target.getName());
        return true;
    }

    private boolean teleportToCapital(Player player, String[] args) {
        String countryId;
        String ownCountry = this.stringVariable("country.player::" + String.valueOf(player.getUniqueId()));
        if (args.length == 0) {
            if (ownCountry == null) {
                player.sendMessage(String.valueOf(ChatColor.RED) + "\u0412\u044b \u043d\u0435 \u0441\u043e\u0441\u0442\u043e\u0438\u0442\u0435 \u0432 \u0441\u0442\u0440\u0430\u043d\u0435. \u0423\u043a\u0430\u0436\u0438\u0442\u0435: /capital <\u0441\u0442\u0440\u0430\u043d\u0430>");
                return true;
            }
            countryId = ownCountry;
        } else {
            String requestedName = String.join((CharSequence)" ", args);
            countryId = this.stringVariable("country.name-id::" + requestedName.toLowerCase(Locale.ROOT));
            if (countryId == null) {
                player.sendMessage(String.valueOf(ChatColor.RED) + "\u0421\u0442\u0440\u0430\u043d\u0430 \u0441 \u0442\u0430\u043a\u0438\u043c \u043d\u0430\u0437\u0432\u0430\u043d\u0438\u0435\u043c \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u0430.");
                return true;
            }
        }
        String countryName = this.stringVariable("country.name::" + countryId);
        Object spawnValue = this.variable("country.spawn::" + countryId);
        if (!(spawnValue instanceof Location)) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "\u0421\u0442\u043e\u043b\u0438\u0446\u0430 \u044d\u0442\u043e\u0439 \u0441\u0442\u0440\u0430\u043d\u044b \u0435\u0449\u0451 \u043d\u0435 \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u0430.");
            return true;
        }
        Location capital = (Location)spawnValue;
        boolean own = countryId.equals(ownCountry);
        if (!own && !Boolean.TRUE.equals(this.variable("country.public-capital::" + countryId))) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "\u042d\u0442\u0430 \u0441\u0442\u0440\u0430\u043d\u0430 \u0437\u0430\u043f\u0440\u0435\u0442\u0438\u043b\u0430 \u0447\u0443\u0436\u0438\u043c \u0438\u0433\u0440\u043e\u043a\u0430\u043c \u0442\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0438\u0440\u043e\u0432\u0430\u0442\u044c\u0441\u044f \u0432 \u0441\u0442\u043e\u043b\u0438\u0446\u0443.");
            return true;
        }
        player.sendMessage(String.valueOf(ChatColor.GREEN) + "\u0422\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0430\u0446\u0438\u044f \u0432 \u0441\u0442\u043e\u043b\u0438\u0446\u0443 \u0447\u0435\u0440\u0435\u0437 5 \u0441\u0435\u043a\u0443\u043d\u0434.");
        this.startTeleport(player, null, capital.clone(), "\u0421\u0442\u043e\u043b\u0438\u0446\u0430: " + (countryName == null ? countryId : countryName));
        return true;
    }

    private void startTeleport(Player player, UUID targetId, Location fixedDestination, String title) {
        this.cancelTeleport(player.getUniqueId(), null);
        BossBar bar = Bukkit.createBossBar((String)(title + " \u2014 5.0 \u0441\u0435\u043a."), (BarColor)BarColor.YELLOW, (BarStyle)BarStyle.SEGMENTED_10, (BarFlag[])new BarFlag[0]);
        bar.setProgress(1.0);
        bar.addPlayer(player);
        ActiveTeleport active = new ActiveTeleport(targetId, fixedDestination, bar);
        this.activeTeleports.put(player.getUniqueId(), active);
        active.task = Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, () -> this.tickTeleport(player.getUniqueId(), title), 2L, 2L);
    }

    private void tickTeleport(UUID playerId, String title) {
        ActiveTeleport active = this.activeTeleports.get(playerId);
        Player player = Bukkit.getPlayer((UUID)playerId);
        if (active == null || player == null || !player.isOnline()) {
            this.cancelTeleport(playerId, null);
            return;
        }
        active.elapsedTicks += 2;
        int remaining = Math.max(0, 100 - active.elapsedTicks);
        active.bar.setProgress(Math.max(0.0, Math.min(1.0, (double)remaining / 100.0)));
        double seconds = (double)remaining / 20.0;
        active.bar.setTitle(title + " \u2014 " + String.format(Locale.US, "%.1f", seconds) + " \u0441\u0435\u043a.");
        if (remaining > 0) {
            return;
        }
        this.activeTeleports.remove(playerId);
        active.close();
        if (active.targetId != null) {
            Player target = Bukkit.getPlayer((UUID)active.targetId);
            if (target == null || !target.isOnline()) {
                player.sendMessage(String.valueOf(ChatColor.RED) + "\u0422\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0430\u0446\u0438\u044f \u043e\u0442\u043c\u0435\u043d\u0435\u043d\u0430: \u0438\u0433\u0440\u043e\u043a \u0432\u044b\u0448\u0435\u043b \u0441 \u0441\u0435\u0440\u0432\u0435\u0440\u0430.");
                return;
            }
            boolean success = VanillaTeleport.toPlayer(player, target);
            if (success) {
                player.sendMessage(String.valueOf(ChatColor.GREEN) + "\u0422\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0430\u0446\u0438\u044f \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u0430.");
            } else {
                player.sendMessage(String.valueOf(ChatColor.RED) + "\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u044c \u0442\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0430\u0446\u0438\u044e.");
            }
            return;
        }
        Location destination = active.fixedDestination;
        if (destination == null || destination.getWorld() == null) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u043e\u043f\u0440\u0435\u0434\u0435\u043b\u0438\u0442\u044c \u043c\u0435\u0441\u0442\u043e \u0442\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0430\u0446\u0438\u0438.");
            return;
        }
        this.executeVanillaTeleport(player, destination);
    }

    private void executeVanillaTeleport(Player player, Location location) {
        boolean success = VanillaTeleport.toLocation(player, location);
        player.sendMessage(success ? String.valueOf(ChatColor.GREEN) + "\u0422\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0430\u0446\u0438\u044f \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u0430." : String.valueOf(ChatColor.RED) + "\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u044c \u0442\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0430\u0446\u0438\u044e.");
    }

    @EventHandler(ignoreCancelled=true)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        if (this.activeTeleports.containsKey(player.getUniqueId())) {
            this.cancelTeleport(player.getUniqueId(), String.valueOf(ChatColor.RED) + "\u0422\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0430\u0446\u0438\u044f \u043e\u0442\u043c\u0435\u043d\u0435\u043d\u0430 \u0438\u0437-\u0437\u0430 \u043f\u043e\u043b\u0443\u0447\u0435\u043d\u043d\u043e\u0433\u043e \u0443\u0440\u043e\u043d\u0430.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        this.cancelTeleport(playerId, null);
        this.incomingRequests.remove(playerId);
        for (LinkedHashMap<UUID, Long> requests : this.incomingRequests.values()) {
            requests.remove(playerId);
        }
    }

    void shutdown() {
        for (ActiveTeleport active : new ArrayList<ActiveTeleport>(this.activeTeleports.values())) {
            active.close();
        }
        this.activeTeleports.clear();
        this.incomingRequests.clear();
    }

    private void cancelTeleport(UUID playerId, String message) {
        ActiveTeleport active = this.activeTeleports.remove(playerId);
        if (active != null) {
            active.close();
        }
        Player player = Bukkit.getPlayer((UUID)playerId);
        if (message != null && player != null) {
            player.sendMessage(message);
        }
    }

    private void cleanupExpiredRequests(UUID targetId) {
        LinkedHashMap<UUID, Long> requests = this.incomingRequests.get(targetId);
        if (requests == null) {
            return;
        }
        long now = System.currentTimeMillis();
        requests.entrySet().removeIf(entry -> (Long)entry.getValue() <= now);
        if (requests.isEmpty()) {
            this.incomingRequests.remove(targetId);
        }
    }

    private boolean isLoggedIn(Player player) {
        return Boolean.TRUE.equals(this.variable("auth.logged-in::" + player.getName()));
    }

    private Object variable(String name) {
        return Variables.getVariable((String)name, null, (boolean)false);
    }

    private String stringVariable(String name) {
        Object value = this.variable(name);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return List.of();
        }
        Player player = (Player)sender;
        if (args.length != 1) {
            return List.of();
        }
        if (command.getName().equalsIgnoreCase("capital")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            ArrayList<String> countries = new ArrayList<String>();
            Iterator iterator = Variables.getVariableIterator((String)"country.name::*", (boolean)false, null);
            while (iterator != null && iterator.hasNext()) {
                String name2;
                Pair pair = (Pair)iterator.next();
                Object value = pair.getSecond();
                if (value == null || !(name2 = value.toString()).toLowerCase(Locale.ROOT).startsWith(prefix)) continue;
                countries.add(name2);
            }
            return countries;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().filter(target -> !target.getUniqueId().equals(player.getUniqueId())).map(Player::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private static final class ActiveTeleport {
        private final UUID targetId;
        private final Location fixedDestination;
        private final BossBar bar;
        private BukkitTask task;
        private int elapsedTicks = 0;

        private ActiveTeleport(UUID targetId, Location fixedDestination, BossBar bar) {
            this.targetId = targetId;
            this.fixedDestination = fixedDestination;
            this.bar = bar;
        }

        private void close() {
            if (this.task != null) {
                this.task.cancel();
                this.task = null;
            }
            this.bar.removeAll();
        }
    }
}

