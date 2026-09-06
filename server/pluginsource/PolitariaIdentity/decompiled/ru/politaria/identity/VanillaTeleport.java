/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package ru.politaria.identity;

import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class VanillaTeleport {
    private VanillaTeleport() {
    }

    static boolean toPlayer(Player player, Player target) {
        return VanillaTeleport.execute(player, target.getLocation(), "minecraft:tp " + player.getName() + " " + target.getName());
    }

    static boolean toLocation(Player player, Location destination) {
        if (destination == null || destination.getWorld() == null) {
            return false;
        }
        try {
            destination.checkFinite();
        }
        catch (IllegalArgumentException invalidLocation) {
            return false;
        }
        String command = String.format(Locale.ROOT, "minecraft:execute in %s run minecraft:tp %s %.4f %.4f %.4f %.4f %.4f", destination.getWorld().getKey(), player.getName(), destination.getX(), destination.getY(), destination.getZ(), Float.valueOf(destination.getYaw()), Float.valueOf(destination.getPitch()));
        return VanillaTeleport.execute(player, destination, command);
    }

    private static boolean execute(Player player, Location destination, String command) {
        if (!Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)command)) {
            return false;
        }
        Location actual = player.getLocation();
        return actual.getWorld() != null && actual.getWorld().equals((Object)destination.getWorld()) && actual.distanceSquared(destination) < 1.0E-6;
    }
}

