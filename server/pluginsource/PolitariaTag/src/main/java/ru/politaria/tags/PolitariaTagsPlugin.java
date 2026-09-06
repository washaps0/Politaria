package ru.politaria.tags;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class PolitariaTagsPlugin extends JavaPlugin implements Listener {

    private static final List<String> ORDER = List.of("Creator", "Dev", "Admin", "Media");
    private static final Set<String> MANUAL_TAGS = Set.of("Creator", "Dev", "Media");

    private final Map<UUID, LinkedHashSet<String>> tags = new HashMap<>();
    private final Map<UUID, TextDisplay> displays = new HashMap<>();
    private final Map<UUID, PermissionAttachment> mediaAttachments = new HashMap<>();

    private File tagsFile;
    private YamlConfiguration tagsConfig;
    private BukkitTask updateTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadTags();
        Bukkit.getPluginManager().registerEvents(this, this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            applyMediaPermissions(player);
            refreshDisplay(player);
        }

        long period = Math.max(1L, getConfig().getLong("update-period", 2L));
        updateTask = Bukkit.getScheduler().runTaskTimer(this, this::tickDisplays, 1L, period);
        getLogger().info("PolitariaTags enabled.");
    }

    @Override
    public void onDisable() {
        if (updateTask != null) updateTask.cancel();
        for (TextDisplay display : displays.values()) {
            if (display != null && display.isValid()) display.remove();
        }
        displays.clear();
        for (PermissionAttachment attachment : mediaAttachments.values()) {
            try { attachment.remove(); } catch (Exception ignored) {}
        }
        mediaAttachments.clear();
        saveTags();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> {
            applyMediaPermissions(player);
            refreshDisplay(player);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        TextDisplay display = displays.remove(id);
        if (display != null && display.isValid()) display.remove();
        PermissionAttachment attachment = mediaAttachments.remove(id);
        if (attachment != null) {
            try { attachment.remove(); } catch (Exception ignored) {}
        }
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
            sender.sendMessage("§7Теги §f" + target.getName() + "§7: " +
                    (effective.isEmpty() ? "§8нет" : "§f" + String.join("§7, §f", effective)));
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
            result = result.append(Component.text("[" + tag + "]", color(tag))
                    .decorate(TextDecoration.BOLD));
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

    private void tickDisplays() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshDisplay(player);
        }
    }

    private void refreshDisplay(Player player) {
        List<String> effective = effectiveTags(player);
        UUID id = player.getUniqueId();

        if (effective.isEmpty()) {
            TextDisplay old = displays.remove(id);
            if (old != null && old.isValid()) old.remove();
            return;
        }

        TextDisplay display = displays.get(id);
        if (display == null || !display.isValid() || !display.getWorld().equals(player.getWorld())) {
            if (display != null && display.isValid()) display.remove();
            display = createDisplay(player);
            displays.put(id, display);
        }

        display.text(tagComponent(player));
        double height = getConfig().getDouble("label-height", 2.85D);
        Location wanted = player.getLocation().add(0.0D, height, 0.0D);
        if (display.getLocation().distanceSquared(wanted) > 0.0004D) {
            display.teleport(wanted);
        }
    }

    private TextDisplay createDisplay(Player player) {
        double height = getConfig().getDouble("label-height", 2.85D);
        Location location = player.getLocation().add(0.0D, height, 0.0D);
        return player.getWorld().spawn(location, TextDisplay.class, display -> {
            display.text(tagComponent(player));
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(true);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setGravity(false);
            display.setViewRange(1.0f);
            display.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(),
                    new Vector3f(0.9f, 0.9f, 0.9f),
                    new AxisAngle4f()
            ));
        });
    }

    private void applyMediaPermissions(Player player) {
        UUID id = player.getUniqueId();
        PermissionAttachment old = mediaAttachments.remove(id);
        if (old != null) {
            try { old.remove(); } catch (Exception ignored) {}
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
