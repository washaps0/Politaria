package ru.politaria.apartments;

import ch.njol.skript.variables.Variables;
import ch.njol.util.Pair;
import java.util.*;
import org.bukkit.entity.Player;

final class SkriptBridge {
    Object get(String key) { return Variables.getVariable(key, null, false); }
    void set(String key, Object value) { Variables.setVariable(key, value, null, false); }
    void delete(String key) { Variables.deleteVariable(key, null, false); }
    String text(String key) { Object v=get(key); return v==null?"":String.valueOf(v); }
    double number(String key, double fallback) { Object v=get(key); return v instanceof Number n?n.doubleValue():fallback; }
    String country(UUID uuid) { return text("country.player::"+uuid); }
    String country(Player p) { return country(p.getUniqueId()); }
    String countryName(String id) { return text("country.name::"+id); }
    boolean countryExists(String id) { return !id.isBlank() && !countryName(id).isBlank(); }
    boolean member(String id, UUID uuid) { return id.equals(country(uuid)); }
    String role(String id, UUID uuid) {
        if(text("country.owner::"+id).equals(uuid.toString())) return "owner";
        String role=text("country.role-of::"+id+"::"+uuid); return role.isBlank()?"citizen":role;
    }
    boolean manages(Player p) {
        String id=country(p); if(id.isBlank())return false;
        if(text("country.owner::"+id).equals(p.getUniqueId().toString()))return true;
        return Boolean.TRUE.equals(get("country.role-perm::"+id+"::"+role(id,p.getUniqueId())+"::APARTMENTS"));
    }
    double balance(UUID uuid) { return number("economy.balance::"+uuid,200); }
    void balance(UUID uuid,double value) { set("economy.balance::"+uuid,Math.round(value*100.0)/100.0); }
    double treasury(String id) { return number("country.treasury::"+id,0); }
    void treasury(String id,double value) { set("country.treasury::"+id,Math.round(value*100.0)/100.0); }
    String chunkOwner(String world,int x,int z) {
        return text("country.claim::v2|"+world+"|"+Math.floorDiv(x,16)+"|"+Math.floorDiv(z,16));
    }
    Map<String,String> roles(String id) {
        Map<String,String> out=new LinkedHashMap<>(); out.put("citizen","Гражданин");
        String wildcard="country.role-name::"+id+"::*";
        Object tree=get(wildcard);
        if(tree instanceof Map<?,?> map) for(var e:map.entrySet()) out.put(String.valueOf(e.getKey()),String.valueOf(e.getValue()));
        try {
            Iterator<Pair<String,Object>> it=Variables.getVariableIterator(wildcard,false,null);
            while(it.hasNext()) {
                Pair<String,Object> pair=it.next(); String key=pair.getFirst();
                String prefix="country.role-name::"+id+"::";
                if(key.startsWith(prefix)) key=key.substring(prefix.length());
                if(!key.equals("*")&&!key.isBlank())out.put(key,String.valueOf(pair.getSecond()));
            }
        } catch(Exception ignored) { }
        return out;
    }
    Set<String> countryIds() {
        Set<String> out=new HashSet<>(); String wildcard="country.name::*";
        Object tree=get(wildcard); if(tree instanceof Map<?,?>map)for(Object k:map.keySet())out.add(String.valueOf(k));
        try {
            Iterator<Pair<String,Object>>it=Variables.getVariableIterator(wildcard,false,null);
            while(it.hasNext()){String k=it.next().getFirst();if(k.startsWith("country.name::"))k=k.substring(14);if(!k.equals("*"))out.add(k);}
        }catch(Exception ignored){}
        return out;
    }
    int claimCount(String id) {
        int count=0; String wildcard="country.claims::"+id+"::*";
        Object tree=get(wildcard); if(tree instanceof Map<?,?>map)return map.size();
        try { Iterator<Pair<String,Object>>it=Variables.getVariableIterator(wildcard,false,null);while(it.hasNext()){it.next();count++;} }catch(Exception ignored){}
        return count;
    }
}
