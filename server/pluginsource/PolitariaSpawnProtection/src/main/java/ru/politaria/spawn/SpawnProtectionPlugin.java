package ru.politaria.spawn;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

public final class SpawnProtectionPlugin extends JavaPlugin implements Listener {
    private static final String SAFE_SHOT = "politaria-spawn-safe-shot";
    private String worldName;
    private double radius;
    private int scanBudget;
    private final Queue<Scan> scans = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        worldName = getConfig().getString("world", "world");
        radius = getConfig().getDouble("radius", 50);
        if (!Double.isFinite(radius) || radius <= 0 || radius > 1000) radius = 50;
        scanBudget = Math.max(256, Math.min(16384, getConfig().getInt("fire-scan-blocks-per-tick", 4096)));
        getServer().getPluginManager().registerEvents(this, this);
        World world = Bukkit.getWorld(worldName);
        if (world != null) enqueueLoaded(world);
        getServer().getScheduler().runTaskTimer(this, this::scanFire, 1, 1);
        getServer().getScheduler().runTaskTimer(this, () -> {
            World current = Bukkit.getWorld(worldName);
            if (current == null) return;
            for (LivingEntity entity : current.getLivingEntities()) {
                if (entity.getFireTicks() > 0 && protectedAt(entity.getLocation())) entity.setFireTicks(0);
            }
        }, 20, 20);
        getLogger().info("Spawn protection: " + worldName + ", horizontal radius " + radius + ", all heights.");
    }

    private boolean protectedAt(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) return false;
        Location spawn = loc.getWorld().getSpawnLocation();
        // /spawn teleports to the centre of this block.
        double dx = loc.getX() - (spawn.getBlockX() + 0.5);
        double dz = loc.getZ() - (spawn.getBlockZ() + 0.5);
        return dx * dx + dz * dz <= radius * radius;
    }

    private boolean protectedBlock(Block block) {
        return protectedAt(block.getLocation().add(0.5, 0, 0.5));
    }

    private boolean safeSource(Entity source) {
        if (source == null) return false;
        if (source.hasMetadata(SAFE_SHOT) || protectedAt(source.getLocation())) return true;
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter)
            return protectedAt(shooter.getLocation());
        if (source instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Entity shooter)
            return protectedAt(shooter.getLocation());
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        boolean inside = protectedAt(event.getEntity().getLocation());
        switch (event.getCause()) {
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION, FIRE, FIRE_TICK, HOT_FLOOR -> {
                if (inside) {
                    event.setCancelled(true);
                    event.getEntity().setFireTicks(0);
                    return;
                }
            }
            // Lingering damage must not bypass protection after entering spawn.
            case POISON, WITHER, MAGIC -> {
                if (inside) { event.setCancelled(true); return; }
            }
            default -> { }
        }
        if (event instanceof EntityDamageByEntityEvent hit) {
            if (inside || safeSource(hit.getDamager())) event.setCancelled(true);
        }
        // Falling, drowning, hunger and other non-combat damage remain unchanged.
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void projectile(ProjectileLaunchEvent event) {
        if (safeSource(event.getEntity()))
            event.getEntity().setMetadata(SAFE_SHOT, new FixedMetadataValue(this, true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void splash(PotionSplashEvent event) {
        boolean harmful = event.getEntity().getEffects().stream()
            .anyMatch(e -> e.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL);
        if (!harmful) return;
        boolean sourceProtected = safeSource(event.getEntity());
        for (LivingEntity target : event.getAffectedEntities()) {
            if (sourceProtected || protectedAt(target.getLocation())) event.setIntensity(target, 0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void lingering(LingeringPotionSplashEvent event) {
        if (safeSource(event.getEntity()))
            event.getAreaEffectCloud().setMetadata(SAFE_SHOT, new FixedMetadataValue(this, true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void cloud(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        boolean harmful = cloud.getCustomEffects().stream()
            .anyMatch(e -> e.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL);
        if (cloud.getBasePotionType() != null)
            harmful |= cloud.getBasePotionType().getPotionEffects().stream()
                .anyMatch(e -> e.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL);
        // Dragon breath clouds use instant damage as a custom effect.
        if (!harmful) return;
        boolean sourceProtected = safeSource(cloud);
        event.getAffectedEntities().removeIf(e -> sourceProtected || protectedAt(e.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void prime(ExplosionPrimeEvent event) {
        if (protectedAt(event.getEntity().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityExplosion(EntityExplodeEvent event) {
        if (protectedAt(event.getLocation())) event.setCancelled(true);
        else event.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockExplosion(BlockExplodeEvent event) {
        if (protectedBlock(event.getBlock())) event.setCancelled(true);
        else event.blockList().removeIf(this::protectedBlock);
    }

    // In the overworld an anchor can consume itself before BlockExplodeEvent.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void anchor(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block != null
                && block.getType() == Material.RESPAWN_ANCHOR && protectedBlock(block)
                && block.getWorld().getEnvironment() != World.Environment.NETHER)
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hanging(HangingBreakEvent event) {
        if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION && protectedAt(event.getEntity().getLocation()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void spawn(CreatureSpawnEvent event) {
        // Decorations are not mobs. Existing mobs/pets are never deleted.
        if (!(event.getEntity() instanceof ArmorStand) && protectedAt(event.getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void ignite(BlockIgniteEvent event) {
        if (protectedBlock(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void burn(BlockBurnEvent event) {
        if (protectedBlock(event.getBlock())) event.setCancelled(true);
    }

    private static boolean fire(Material type) { return type == Material.FIRE || type == Material.SOUL_FIRE; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void spread(BlockSpreadEvent event) {
        if (fire(event.getNewState().getType()) && protectedBlock(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        if (fire(event.getBlockPlaced().getType()) && protectedBlock(event.getBlockPlaced())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void combust(EntityCombustEvent event) {
        if (protectedAt(event.getEntity().getLocation())) {
            event.setCancelled(true);
            event.getEntity().setFireTicks(0);
        }
    }

    @EventHandler public void chunkLoad(ChunkLoadEvent event) { enqueue(event.getChunk()); }
    @EventHandler public void worldLoad(WorldLoadEvent event) { enqueueLoaded(event.getWorld()); }
    @EventHandler public void spawnChange(SpawnChangeEvent event) { enqueueLoaded(event.getWorld()); }

    private void enqueueLoaded(World world) {
        if (world.getName().equals(worldName)) for (Chunk chunk : world.getLoadedChunks()) enqueue(chunk);
    }

    private void enqueue(Chunk chunk) {
        World world = chunk.getWorld();
        if (!world.getName().equals(worldName)) return;
        Location spawn = world.getSpawnLocation();
        double sx = spawn.getBlockX() + 0.5, sz = spawn.getBlockZ() + 0.5;
        double x = Math.max(chunk.getX() * 16, Math.min(sx, chunk.getX() * 16 + 16));
        double z = Math.max(chunk.getZ() * 16, Math.min(sz, chunk.getZ() * 16 + 16));
        if ((x-sx)*(x-sx) + (z-sz)*(z-sz) > radius * radius) return;
        String key = world.getUID() + ":" + chunk.getX() + ":" + chunk.getZ();
        if (queued.add(key)) scans.add(new Scan(world, chunk.getX(), chunk.getZ(), key));
    }

    private void scanFire() {
        int remaining = scanBudget;
        while (remaining > 0 && !scans.isEmpty()) {
            Scan scan = scans.peek();
            int height = scan.world.getMaxHeight() - scan.world.getMinHeight();
            if (!scan.world.isChunkLoaded(scan.x, scan.z) || scan.index >= 256 * height) {
                queued.remove(scan.key);
                scans.remove();
                continue;
            }
            int index = scan.index++;
            remaining--;
            int x = scan.x * 16 + (index & 15);
            int z = scan.z * 16 + ((index >> 4) & 15);
            int y = scan.world.getMinHeight() + (index >> 8);
            if (!protectedAt(new Location(scan.world, x + 0.5, y, z + 0.5))) continue;
            Block block = scan.world.getBlockAt(x, y, z);
            if (fire(block.getType())) block.setType(Material.AIR, false);
        }
    }

    private static final class Scan {
        final World world;
        final int x, z;
        final String key;
        int index;
        Scan(World world, int x, int z, String key) { this.world=world; this.x=x; this.z=z; this.key=key; }
    }
}
