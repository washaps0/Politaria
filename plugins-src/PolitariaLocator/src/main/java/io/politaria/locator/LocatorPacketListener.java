package io.politaria.locator;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class LocatorPacketListener {

    private final PolitariaLocator plugin;
    private final CountryResolver countryResolver;

    LocatorPacketListener(PolitariaLocator plugin, CountryResolver countryResolver) {
        this.plugin = plugin;
        this.countryResolver = countryResolver;
    }

    void register() {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                plugin,
                PacketType.Play.Server.TRACKED_WAYPOINT
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!plugin.getConfig().getBoolean("enabled", true)) {
                    return;
                }

                PacketContainer packetContainer = event.getPacket();
                ClientboundTrackedWaypointPacket packet = (ClientboundTrackedWaypointPacket) packetContainer.getHandle();
                TrackedWaypoint tracked = packet.waypoint();

                tracked.id().ifLeft(uuid -> {
                    Player viewer = event.getPlayer();

                    // Never interfere with the viewer's own waypoint packet.
                    if (uuid.equals(viewer.getUniqueId())) {
                        return;
                    }

                    Player target = Bukkit.getPlayer(uuid);
                    if (target == null || !target.isOnline()) {
                        return;
                    }

                    // Players without a country are hidden, and players from
                    // another country are hidden. Only fellow citizens pass.
                    if (!countryResolver.areSameCountry(viewer, target)) {
                        event.setCancelled(true);
                    }
                });
            }
        });
    }
}
