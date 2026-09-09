package ru.politaria.tags;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Legacy standalone tag plugin.
 *
 * New PolitariaIdentity versions already contain the full tag system. If
 * PolitariaIdentity is installed this plugin disables itself to avoid duplicate
 * commands, duplicate labels and a second tags.yml.
 *
 * When used standalone, labels are TextDisplay passengers attached to players.
 * They are NOT teleported every tick. This is much more stable on Paper/Purpur
 * and avoids the old floating/lagging label behaviour after host changes.
 */
public final class PolitariaTagsPlugin extends JavaPlugin implements Listener {

    private static final List<String> ORDER = List.of("Creator", "Dev", "Admin", "Media");
    private static final Set<String> MANUAL_TAGS = Set.of("Creator", "Dev", "Media");
    private static final float LABEL_OFFSET = 0.95f;

    private final Map<UUID, LinkedHashSet<String>> tags = new HashMap<>();
    private final Map<UUID, TextDisplay> displays = new HashMap<>();
    private final Map<UUID, PermissionAttachment> mediaAttachments = new HashMap<>();

    private File tagsFile;
    private YamlConfiguration tagsConfig;
    private BukkitTask maintainTask;

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("PolitariaIdentity") != null) {
            getLogger().warning("PolitariaIdentity already contains the tag system. PolitariaTags is legacy and will disable itself to avoid conflicts.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        loadTags();
        Bukkit.getPluginManager().registerEvents(this, this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            applyMediaPermissions(player);
            refreshDisplay(player);
        }

        maintainTask = Bukkit.getScheduler().runTaskTimer(this, this::maintainDisplays, 20L, 20L);
        getLogger().info("PolitariaTags legacy standalone mode enabled.");
    }

    @Override
    public void onDisable() {
        if (maintainTask != null) maintainTask.cancel();

        for (TextDisplay display : displays.values()) {
            if (display != null && display.isValid()) display.remove();
        }
        displays.clear();

        for (PermissionAttachment attachment : mediaAttachments.values()) {
            try {
                attachment.remove();
            } catch (Exception ignored) {
            }
        }
        mediaAttachments.clear();
        saveTags();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;
            applyMediaPermissions(player);
            refreshDisplay(player);
        }, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeDisplay(event.getPlayer());

        PermissionAttachment attachment = mediaAttachments.remove(event.getPlayer().getUniqueId());
        if (attachment != null) {
            try {
                attachment.remove();
            } catch (Exception ignored) {
            }
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        recreateAfterTeleport(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        recreateAfterTeleport(event.getPlayer());
    }

    private void recreateAfterTeleport(Player player) {
        removeDisplay(player);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) refreshDisplay(player);
        }, 2L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("tag")) return false;

        if (!sender.hasPermission("politaria.tags.manage")) {
            sender.sendMessage("§cНет прав.");
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cИгрок должен быть онлайн: §f" + args[1]);
            return true;
        }

        if (action.equals("list")) {
            List<String> effective = effectiveTags(target);
            sender.sendMessage("§7Теги §f" + target.getName() + "§7: "
                    + (effective.isEmpty() ? "§8нет" : "§f" + String.join("§7, §f", effective)));
            return true;
        }

        if (args.length < 3) {
            sendUsage(sender);
            return true;
        }

        String tag = canonicalTag(args[2]);
        if (tag == null) {
            sender.sendMessage("§cНеизвестный тег. Доступно: §fCreator, Dev, Media§c. Admin выдаётся автоматически OP-игрокам.");
            return true;
        }

        if (tag.equals("Admin")) {
            sender.sendMessage("§cAdmin нельзя выдавать вручную: он автоматически отображается у OP.");
            return true;
        }

        UUID id = target.getUniqueId();
        LinkedHashSet<String> playerTags = tags.computeIfAbsent(id, unused -> new LinkedHashSet<>());

        if (action.equals("add")) {
            if (!playerTags.add(tag)) {
                sender.sendMessage("§eУ игрока уже есть тег " + tag + ".");
                return true;
            }
            saveTags();
            applyMediaPermissions(target);
            refreshDisplay(target);
            sender.sendMessage("§aДобавлен тег §f" + tag + " §aигроку §f" + target.getName() + "§a.");
            return true;
        }

        if (action.equals("remove")) {
            if (!playerTags.remove(tag)) {
                sender.sendMessage("§eУ игрока нет тега " + tag + ".");
                return true;
            }
            if (playerTags.isEmpty()) tags.remove(id);
            saveTags();
            applyMediaPermissions(target);
            refreshDisplay(target);
            sender.sendMessage("§aУдалён тег §f" + tag + " §aу игрока §f" + target.getName() + "§a.");
            return true;
        }

        sendUsage(sender);
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§7/tag add <player> <Creator|Dev|Media>");
        sender.sendMessage("§7/tag remove <player> <Creator|Dev|Media>");
        sender.sendMessage("§7/tag list <player>");
        sender.sendMessage("§8Admin появляется автоматически у OP.");
    }

    private String canonicalTag(String input) {
        for (String tag : ORDER) {
            if (tag.equalsIgnoreCase(input)) return tag;
        }
        return null;
    }

    private List<String> effectiveTags(Player player) {
        Set<String> manual = tags.getOrDefault(player.getUniqueId(), new LinkedHashSet<>());
        List<String> result = new ArrayList<>();
        for (String tag : ORDER) {
            if (tag.equals("Admin")) {
                if (player.isOp()) result.add(tag);
            } else if (manual.contains(tag)) {
                result.add(tag);
            }
        }
        return result;
    }

    private Component tagComponent(Player player) {
        List<String> effective = effectiveTags(player);
        Component result = Component.empty();
        for (int i = 0; i < effective.size(); i++) {
            if (i > 0) result = result.append(Component.space());
            String tag = effective.get(i);
            result = result.append(Component.text("[" + tag + "]", color(tag)).decorate(TextDecoration.BOLD));
        }
        return result;
    }

    private NamedTextColor color(String tag) {
        return switch (tag) {
            case "Creator" -> NamedTextColor.LIGHT_PURPLE;
            case "Dev" -> NamedTextColor.AQUA;
            case "Admin" -> NamedTextColor.RED;
            case "Media" -> NamedTextColor.GOLD;
            default -> NamedTextColor.WHITE;
        };
    }

    private void maintainDisplays() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshDisplay(player);
        }
    }

    private void refreshDisplay(Player player) {
        List<String> effective = effectiveTags(player);
        UUID id = player.getUniqueId();

        if (effective.isEmpty()) {
            removeDisplay(player);
            return;
        }

        TextDisplay display = displays.get(id);
        if (display == null || !display.isValid() || display.getVehicle() != player || display.getWorld() != player.getWorld()) {
            removeDisplay(player);
            display = createDisplay(player);
            displays.put(id, display);
        }

        display.text(tagComponent(player));
    }

    private TextDisplay createDisplay(Player player) {
        TextDisplay display = player.getWorld().spawn(player.getLocation(), TextDisplay.class, entity -> {
            entity.text(tagComponent(player));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(true);
            entity.setShadowed(true);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setSilent(true);
            entity.setVisibleByDefault(true);
            entity.setViewRange(1.0f);
            entity.setTransformation(new Transformation(
                    new Vector3f(0.0f, LABEL_OFFSET, 0.0f),
                    new AxisAngle4f(),
                    new Vector3f(0.9f, 0.9f, 0.9f),
                    new AxisAngle4f()
            ));
        });

        player.addPassenger(display);
        return display;
    }

    private void removeDisplay(Player player) {
        TextDisplay display = displays.remove(player.getUniqueId());
        if (display != null && display.isValid()) display.remove();
    }

    private void applyMediaPermissions(Player player) {
        UUID id = player.getUniqueId();
        PermissionAttachment old = mediaAttachments.remove(id);
        if (old != null) {
            try {
                old.remove();
            } catch (Exception ignored) {
            }
        }

        if (!tags.getOrDefault(id, new LinkedHashSet<>()).contains("Media")) {
            player.recalculatePermissions();
            return;
        }

        PermissionAttachment attachment = player.addAttachment(this);
        for (String node : getConfig().getStringList("media-permissions")) {
            attachment.setPermission(node, true);
        }
        mediaAttachments.put(id, attachment);
        player.recalculatePermissions();
    }

    private void loadTags() {
        tagsFile = new File(getDataFolder(), "tags.yml");
        tagsConfig = YamlConfiguration.loadConfiguration(tagsFile);
        tags.clear();

        var section = tagsConfig.getConfigurationSection("players");
        if (section == null) return;

        for (String uuidText : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(uuidText);
                LinkedHashSet<String> loaded = new LinkedHashSet<>();
                for (String raw : section.getStringList(uuidText)) {
                    String tag = canonicalTag(raw);
                    if (tag != null && MANUAL_TAGS.contains(tag)) loaded.add(tag);
                }
                if (!loaded.isEmpty()) tags.put(id, loaded);
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("Invalid UUID in tags.yml: " + uuidText);
            }
        }
    }

    private void saveTags() {
        if (tagsConfig == null || tagsFile == null) return;
        tagsConfig.set("players", null);
        for (Map.Entry<UUID, LinkedHashSet<String>> entry : tags.entrySet()) {
            tagsConfig.set("players." + entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                getLogger().warning("Could not create plugin folder.");
            }
            tagsConfig.save(tagsFile);
        } catch (IOException exception) {
            getLogger().severe("Could not save tags.yml: " + exception.getMessage());
        }
    }
}
