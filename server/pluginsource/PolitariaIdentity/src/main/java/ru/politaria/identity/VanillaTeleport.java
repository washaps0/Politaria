package ru.politaria.identity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Central teleport helper used by TeleportManager.
 *
 * Important for PolitariaIdentity:
 * players have TextDisplay entities attached as passengers for custom nametags.
 * Cross-world teleports can fail or behave incorrectly while an entity has
 * passengers, therefore we detach them before teleporting.
 */
final class VanillaTeleport {

    private VanillaTeleport() {
    }

    static boolean toPlayer(Player player, Player target) {
        if (player == null || target == null) {
            return false;
        }

        if (!player.isOnline() || !target.isOnline()) {
            return false;
        }

        Location destination = target.getLocation().clone();
        return teleport(player, destination);
    }

    static boolean toLocation(Player player, Location destination) {
        if (player == null || destination == null) {
            return false;
        }

        if (!player.isOnline()) {
            return false;
        }

        return teleport(player, destination.clone());
    }

    private static boolean teleport(Player player, Location destination) {
        World world = destination.getWorld();

        if (world == null) {
            return false;
        }

        try {
            destination.checkFinite();
        } catch (IllegalArgumentException exception) {
            return false;
        }

        /*
         * PolitariaIdentity attaches TextDisplays to the player as passengers.
         * Minecraft/Paper can reject cross-dimensional teleportation while
         * passengers are attached.
         *
         * Do not remove the entities here. PolitariaIdentity's world-change
         * handler recreates/removes the old labels after a successful transfer.
         */
        detachPassengers(player);

        /*
         * Make sure the destination chunk exists before a cross-world teleport.
         * This avoids the teleport failing simply because the target chunk has
         * not been loaded yet.
         */
        int chunkX = destination.getBlockX() >> 4;
        int chunkZ = destination.getBlockZ() >> 4;

        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            world.getChunkAt(chunkX, chunkZ).load();
        }

        boolean success;

        try {
            success = player.teleport(
                    destination,
                    PlayerTeleportEvent.TeleportCause.PLUGIN
            );
        } catch (Exception exception) {
            return false;
        }

        if (!success) {
            return false;
        }

        player.setFallDistance(0.0f);

        return true;
    }

    private static void detachPassengers(Player player) {
        /*
         * Player#eject() removes all entities riding the player.
         * Currently these are the custom TextDisplay nametags.
         */
        if (!player.getPassengers().isEmpty()) {
            player.eject();
        }

        /*
         * Also make sure the player himself is not riding an entity.
         * This matters for boats, minecarts, horses, etc., because changing
         * dimensions while mounted is another common reason for teleport
         * rejection.
         */
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
    }
}
