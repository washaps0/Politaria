package ru.politaria.apartments;

import java.time.LocalDate;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;

final class Apartment {
    enum Offer { CLOSED, SALE, RENT }
    final String id;
    String country, name, world;
    int minX, minY, minZ, maxX, maxY, maxZ;
    Offer offer = Offer.CLOSED;
    double price;
    UUID owner;
    final Set<UUID> coowners = new HashSet<>();
    final Set<String> blockedRoles = new HashSet<>();
    boolean publicBuild, publicInteract;
    Location spawn;
    LocalDate rentPaidThrough;

    Apartment(String id) { this.id = id; }
    boolean contains(Location l) {
        return l.getWorld() != null && l.getWorld().getName().equals(world)
            && l.getBlockX() >= minX && l.getBlockX() <= maxX
            && l.getBlockY() >= minY && l.getBlockY() <= maxY
            && l.getBlockZ() >= minZ && l.getBlockZ() <= maxZ;
    }
    boolean overlaps(Apartment b) {
        return world.equals(b.world) && minX <= b.maxX && maxX >= b.minX
            && minY <= b.maxY && maxY >= b.minY && minZ <= b.maxZ && maxZ >= b.minZ;
    }
    long volume() { return (long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1); }
    boolean resident(UUID uuid) { return uuid.equals(owner) || coowners.contains(uuid); }
    void save(ConfigurationSection s) {
        s.set("country", country); s.set("name", name); s.set("world", world);
        s.set("min", List.of(minX,minY,minZ)); s.set("max", List.of(maxX,maxY,maxZ));
        s.set("offer", offer.name()); s.set("price", price);
        s.set("owner", owner == null ? null : owner.toString());
        s.set("coowners", coowners.stream().map(UUID::toString).toList());
        s.set("blocked-roles", new ArrayList<>(blockedRoles));
        s.set("public-build", publicBuild); s.set("public-interact", publicInteract);
        s.set("spawn", spawn); s.set("rent-paid-through", rentPaidThrough == null ? null : rentPaidThrough.toString());
    }
    static Apartment load(String id, ConfigurationSection s) {
        try {
            Apartment a = new Apartment(id);
            a.country=s.getString("country",""); a.name=s.getString("name","Квартира"); a.world=s.getString("world","");
            List<Integer> min=s.getIntegerList("min"), max=s.getIntegerList("max");
            if(min.size()!=3||max.size()!=3) return null;
            a.minX=min.get(0);a.minY=min.get(1);a.minZ=min.get(2);a.maxX=max.get(0);a.maxY=max.get(1);a.maxZ=max.get(2);
            a.offer=Offer.valueOf(s.getString("offer","CLOSED")); a.price=Math.max(0,s.getDouble("price"));
            String owner=s.getString("owner"); if(owner!=null&&!owner.isBlank()) a.owner=UUID.fromString(owner);
            for(String v:s.getStringList("coowners")) try{a.coowners.add(UUID.fromString(v));}catch(Exception ignored){}
            a.blockedRoles.addAll(s.getStringList("blocked-roles"));
            a.publicBuild=s.getBoolean("public-build"); a.publicInteract=s.getBoolean("public-interact");
            a.spawn=s.getLocation("spawn"); String paid=s.getString("rent-paid-through"); if(paid!=null)a.rentPaidThrough=LocalDate.parse(paid);
            return a;
        } catch(Exception ignored) { return null; }
    }
}
