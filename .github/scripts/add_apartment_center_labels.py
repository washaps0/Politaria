from pathlib import Path

java = Path('server/pluginsource/PolitariaApartments/src/main/java/ru/politaria/apartments/ApartmentPlugin.java')
s = java.read_text(encoding='utf-8')

# Imports.
s = s.replace('import net.kyori.adventure.text.Component;\n', 'import net.kyori.adventure.text.Component;\nimport net.kyori.adventure.text.format.NamedTextColor;\nimport net.kyori.adventure.text.format.TextDecoration;\n')
s = s.replace('import org.bukkit.entity.Player;\n', 'import org.bukkit.entity.*;\n')

# Fields.
s = s.replace('    private NamespacedKey wandKey;\n', '    private NamespacedKey wandKey;\n    private NamespacedKey apartmentLabelKey;\n')
s = s.replace('    private final Map<UUID,Prompt> prompts=new ConcurrentHashMap<>();\n', '    private final Map<UUID,Prompt> prompts=new ConcurrentHashMap<>();\n    private final Map<String,UUID> apartmentLabels=new HashMap<>();\n')

# Initialization and shutdown.
s = s.replace('        sk=new SkriptBridge();store=new ApartmentStore(getDataFolder());store.load();wandKey=new NamespacedKey(this,"selection-wand");\n', '        sk=new SkriptBridge();store=new ApartmentStore(getDataFolder());store.load();wandKey=new NamespacedKey(this,"selection-wand");apartmentLabelKey=new NamespacedKey(this,"apartment-label");\n')
s = s.replace('        Bukkit.getScheduler().runTaskTimer(this,this::purgeInvalid,1200,1200);\n', '        Bukkit.getScheduler().runTaskTimer(this,this::purgeInvalid,1200,1200);\n        Bukkit.getScheduler().runTask(this,this::refreshAllApartmentLabels);\n')
s = s.replace('    @Override public void onDisable(){if(midnightTask!=null)midnightTask.cancel();store.save();selections.clear();prompts.clear();}\n', '    @Override public void onDisable(){if(midnightTask!=null)midnightTask.cancel();removeAllApartmentLabels();store.save();selections.clear();prompts.clear();}\n')

# Label helpers after offer().
needle = '    private String offer(Apartment a){return switch(a.offer){case CLOSED->"Закрыта";case SALE->"Продажа: "+money(a.price);case RENT->"Аренда: "+money(a.price)+"/день";};}\n'
if needle not in s:
    raise SystemExit('offer() anchor not found')
helpers = needle + '''    private Location apartmentCenter(Apartment a){\n        World world=Bukkit.getWorld(a.world);if(world==null)return null;\n        double x=(a.minX+a.maxX+1)/2.0,y=(a.minY+a.maxY+1)/2.0,z=(a.minZ+a.maxZ+1)/2.0;\n        return new Location(world,x,y,z);\n    }\n    private Component apartmentLabelText(Apartment a){\n        Component title=Component.text(a.name,NamedTextColor.GOLD).decorate(TextDecoration.BOLD);\n        Component status;\n        if(a.owner!=null){\n            String owner=Optional.ofNullable(Bukkit.getOfflinePlayer(a.owner).getName()).orElse("Владелец");\n            if(a.offer==Apartment.Offer.RENT)status=Component.text("Арендует: "+owner+" • "+money(a.price)+" монет/день",NamedTextColor.AQUA);\n            else status=Component.text("Владелец: "+owner,NamedTextColor.GRAY);\n        }else if(a.offer==Apartment.Offer.SALE){\n            status=Component.text("Продажа • "+money(a.price)+" монет",NamedTextColor.GREEN);\n        }else if(a.offer==Apartment.Offer.RENT){\n            status=Component.text("Аренда • "+money(a.price)+" монет/день",NamedTextColor.AQUA);\n        }else{\n            status=Component.text("Не выставлена",NamedTextColor.GRAY);\n        }\n        return title.append(Component.newline()).append(status);\n    }\n    private void removeApartmentLabel(String apartmentId){\n        UUID uuid=apartmentLabels.remove(apartmentId);\n        if(uuid==null)return;\n        Entity entity=Bukkit.getEntity(uuid);if(entity!=null)entity.remove();\n    }\n    private void removeAllApartmentLabels(){\n        for(UUID uuid:new ArrayList<>(apartmentLabels.values())){Entity entity=Bukkit.getEntity(uuid);if(entity!=null)entity.remove();}\n        apartmentLabels.clear();\n    }\n    private void refreshApartmentLabel(Apartment a){\n        removeApartmentLabel(a.id);\n        Location center=apartmentCenter(a);if(center==null)return;\n        TextDisplay display=center.getWorld().spawn(center,TextDisplay.class,d->{\n            d.text(apartmentLabelText(a));\n            d.setBillboard(Display.Billboard.CENTER);\n            d.setAlignment(TextDisplay.TextAlignment.CENTER);\n            d.setShadowed(true);\n            d.setSeeThrough(true);\n            d.setLineWidth(240);\n            d.setPersistent(false);\n            d.getPersistentDataContainer().set(apartmentLabelKey,PersistentDataType.STRING,a.id);\n        });\n        apartmentLabels.put(a.id,display.getUniqueId());\n    }\n    private void refreshAllApartmentLabels(){\n        removeAllApartmentLabels();\n        for(Apartment a:store.all.values())refreshApartmentLabel(a);\n    }\n'''
s = s.replace(needle, helpers, 1)

# Settlement: refresh after rent status/payment processing.
old = '                if(owner.isOnline())Objects.requireNonNull(owner.getPlayer()).sendMessage("§6Аренда квартиры «"+a.name+"»: §f-"+money(total)+" монет §7за "+due+" дн.");\n            }\n'
new = '                if(owner.isOnline())Objects.requireNonNull(owner.getPlayer()).sendMessage("§6Аренда квартиры «"+a.name+"»: §f-"+money(total)+" монет §7за "+due+" дн.");\n            }\n            refreshApartmentLabel(a);\n'
if old not in s:
    raise SystemExit('settle rent anchor not found')
s = s.replace(old,new,1)

# purgeInvalid refresh.
s = s.replace('        if(changed)store.save();\n', '        if(changed){store.save();refreshAllApartmentLabels();}\n', 1)

# Manager offer mode change refresh.
old = '                if(slot==12){a.offer=switch(a.offer){case CLOSED->Apartment.Offer.SALE;case SALE->Apartment.Offer.RENT;case RENT->Apartment.Offer.CLOSED;};store.save();openManager(p,a);return;}\n'
new = '                if(slot==12){a.offer=switch(a.offer){case CLOSED->Apartment.Offer.SALE;case SALE->Apartment.Offer.RENT;case RENT->Apartment.Offer.CLOSED;};store.save();refreshApartmentLabel(a);openManager(p,a);return;}\n'
if old not in s:
    raise SystemExit('offer cycle anchor not found')
s = s.replace(old,new,1)

# Delete apartment label too.
old = '                if(slot==30&&e.isShiftClick()&&e.isLeftClick()){store.all.remove(a.id);store.save();p.closeInventory();p.sendMessage("§eКвартира удалена без возврата денег.");return;}if(slot==31){openList(p,1);}\n'
new = '                if(slot==30&&e.isShiftClick()&&e.isLeftClick()){removeApartmentLabel(a.id);store.all.remove(a.id);store.save();p.closeInventory();p.sendMessage("§eКвартира удалена без возврата денег.");return;}if(slot==31){openList(p,1);}\n'
if old not in s:
    raise SystemExit('delete anchor not found')
s = s.replace(old,new,1)

# Name/price prompt refresh.
s = s.replace('        store.save();p.sendMessage("§aНастройка сохранена.");openManager(p,a);\n', '        store.save();refreshApartmentLabel(a);p.sendMessage("§aНастройка сохранена.");openManager(p,a);\n', 1)

# Purchase/rent refresh.
old = '        sk.balance(p.getUniqueId(),balance-a.price);sk.treasury(a.country,sk.treasury(a.country)+a.price);a.owner=p.getUniqueId();a.coowners.clear();a.publicBuild=false;a.publicInteract=false;a.rentPaidThrough=a.offer==Apartment.Offer.RENT?LocalDate.now(zone):null;store.save();\n'
new = '        sk.balance(p.getUniqueId(),balance-a.price);sk.treasury(a.country,sk.treasury(a.country)+a.price);a.owner=p.getUniqueId();a.coowners.clear();a.publicBuild=false;a.publicInteract=false;a.rentPaidThrough=a.offer==Apartment.Offer.RENT?LocalDate.now(zone):null;store.save();refreshApartmentLabel(a);\n'
if old not in s:
    raise SystemExit('buy anchor not found')
s = s.replace(old,new,1)

# Creation refresh.
old = '        store.all.put(a.id,a);store.save();removeWand(p);selections.remove(p.getUniqueId());p.sendMessage("§aКвартира создана. Настройте название, режим и стоимость.");openManager(p,a);\n'
new = '        store.all.put(a.id,a);store.save();refreshApartmentLabel(a);removeWand(p);selections.remove(p.getUniqueId());p.sendMessage("§aКвартира создана. Настройте название, режим и стоимость.");openManager(p,a);\n'
if old not in s:
    raise SystemExit('create anchor not found')
s = s.replace(old,new,1)

java.write_text(s,encoding='utf-8')

# Make Gradle build portable on GitHub runners by resolving Paper API from its Maven repo.
build = Path('server/pluginsource/PolitariaApartments/build.gradle')
b = build.read_text(encoding='utf-8')
old_dep = "dependencies {\n    compileOnly files(new File(System.getProperty('user.home'), '.m2/repository/io/papermc/paper/paper-api/1.21.1-R0.1-SNAPSHOT/paper-api-1.21.1-R0.1-SNAPSHOT.jar'))\n"
new_dep = "repositories { maven { url = uri('https://repo.papermc.io/repository/maven-public/') } }\ndependencies {\n    compileOnly 'io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT'\n"
if old_dep in b:
    b=b.replace(old_dep,new_dep,1)
build.write_text(b,encoding='utf-8')

print('Apartment center labels patch applied')
