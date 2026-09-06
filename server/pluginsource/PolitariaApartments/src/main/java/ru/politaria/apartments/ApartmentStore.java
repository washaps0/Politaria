package ru.politaria.apartments;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class ApartmentStore {
    private final File file;
    final Map<String,Apartment> all=new LinkedHashMap<>();
    ApartmentStore(File folder){file=new File(folder,"apartments.yml");}
    void load(){
        all.clear(); YamlConfiguration y=YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root=y.getConfigurationSection("apartments"); if(root==null)return;
        for(String id:root.getKeys(false)){Apartment a=Apartment.load(id,root.getConfigurationSection(id));if(a!=null)all.put(id,a);}
    }
    void save(){
        YamlConfiguration y=new YamlConfiguration();
        for(Apartment a:all.values())a.save(y.createSection("apartments."+a.id));
        try{y.save(file);}catch(IOException e){throw new IllegalStateException("Cannot save "+file,e);}
    }
    Apartment at(org.bukkit.Location loc){for(Apartment a:all.values())if(a.contains(loc))return a;return null;}
    Apartment owned(UUID uuid){for(Apartment a:all.values())if(uuid.equals(a.owner))return a;return null;}
    List<Apartment> country(String id){return all.values().stream().filter(a->a.country.equals(id)).toList();}
}
