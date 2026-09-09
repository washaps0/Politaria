package ru.politaria.apartments;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ApartmentPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private SkriptBridge sk;
    private ApartmentStore store;
    private NamespacedKey wandKey;
    private NamespacedKey apartmentLabelKey;
    private ZoneId zone;
    private int maxName,maxSide,maxVolume;
    private double maxPrice;
    private final Map<UUID,Selection> selections=new HashMap<>();
    private final Map<UUID,Prompt> prompts=new ConcurrentHashMap<>();
    private final Map<String,UUID> apartmentLabels=new HashMap<>();
    private BukkitTask midnightTask;
    private final Map<UUID,BukkitTask> homeTeleportTasks=new HashMap<>();

    @Override public void onEnable(){
        saveDefaultConfig(); zone=ZoneId.of(getConfig().getString("timezone","Europe/Moscow"));
        maxName=getConfig().getInt("max-name-length",32);maxSide=getConfig().getInt("max-region-side",64);
        maxVolume=getConfig().getInt("max-region-volume",100000);maxPrice=getConfig().getDouble("max-price",100000000);
        sk=new SkriptBridge();store=new ApartmentStore(getDataFolder());store.load();wandKey=new NamespacedKey(this,"selection-wand");apartmentLabelKey=new NamespacedKey(this,"apartment-label");
        Objects.requireNonNull(getCommand("apartments")).setExecutor(this);Objects.requireNonNull(getCommand("apartment")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this,this);
        Bukkit.getScheduler().runTaskTimer(this,this::purgeInvalid,1200,1200);
        Bukkit.getScheduler().runTask(this,this::refreshAllApartmentLabels);
        getLogger().info("Loaded "+store.all.size()+" apartments; settlement timezone "+zone+".");
    }
    @Override public void onDisable(){if(midnightTask!=null)midnightTask.cancel();for(BukkitTask task:homeTeleportTasks.values())task.cancel();homeTeleportTasks.clear();removeAllApartmentLabels();store.save();selections.clear();prompts.clear();}

    @EventHandler public void serverLoaded(ServerLoadEvent event){
        // Skript loads its variables and scripts during delayed server initialization.
        // ServerLoadEvent is the first safe point to read country/economy variables.
        Bukkit.getScheduler().runTask(this,()->{settle();purgeInvalid();scheduleMidnight();});
    }

    private void scheduleMidnight(){
        ZonedDateTime now=ZonedDateTime.now(zone),next=now.toLocalDate().plusDays(1).atStartOfDay(zone);
        // Round up: running even a few milliseconds before midnight would still see yesterday
        // and could otherwise postpone settlement for a whole day.
        long millis=Duration.between(now,next).toMillis();
        long ticks=Math.max(1,(millis+49)/50);
        if(midnightTask!=null)midnightTask.cancel();
        midnightTask=Bukkit.getScheduler().runTaskLater(this,()->{settle();scheduleMidnight();},ticks);
    }
    private void settle(){
        LocalDate today=LocalDate.now(zone);String raw=getConfig().getString("last-settlement-date");
        LocalDate last;try{last=raw==null?today:LocalDate.parse(raw);}catch(Exception e){last=today;}
        long days=Math.max(0,ChronoUnit.DAYS.between(last,today));
        if(days>0){settleCountryTaxes(days);settleRents(today);getConfig().set("last-settlement-date",today.toString());saveConfig();store.save();}
        else if(raw==null){getConfig().set("last-settlement-date",today.toString());saveConfig();}
    }
    private void settleCountryTaxes(long days){
        double perChunk=sk.number("country.tax-per-chunk",15);
        for(String id:sk.countryIds()){
            double tax=sk.claimCount(id)*perChunk*days;if(tax<=0)continue;
            sk.treasury(id,sk.treasury(id)-tax);
            for(Player p:Bukkit.getOnlinePlayers())if(sk.country(p).equals(id))p.sendMessage("§6Содержание территории за "+days+" дн.: §f-"+money(tax)+" монет§6. Казна: §f"+money(sk.treasury(id))+"§6.");
        }
    }
    private void settleRents(LocalDate today){
        for(Apartment a:store.all.values()){
            if(a.owner==null||a.offer!=Apartment.Offer.RENT)continue;
            if(a.rentPaidThrough==null)a.rentPaidThrough=today.minusDays(1);
            long due=Math.max(0,ChronoUnit.DAYS.between(a.rentPaidThrough,today));if(due==0)continue;
            double total=a.price*due,balance=sk.balance(a.owner);
            OfflinePlayer owner=Bukkit.getOfflinePlayer(a.owner);
            if(balance+0.00001<total){
                a.owner=null;a.coowners.clear();a.publicBuild=false;a.publicInteract=false;a.rentPaidThrough=null;
                if(owner.isOnline())Objects.requireNonNull(owner.getPlayer()).sendMessage("§cАренда квартиры «"+a.name+"» прекращена: недостаточно денег за "+due+" дн.");
            }else{
                sk.balance(owner.getUniqueId(),balance-total);sk.treasury(a.country,sk.treasury(a.country)+total);a.rentPaidThrough=today;
                if(owner.isOnline())Objects.requireNonNull(owner.getPlayer()).sendMessage("§6Аренда квартиры «"+a.name+"»: §f-"+money(total)+" монет §7за "+due+" дн.");
            }
            refreshApartmentLabel(a);
        }
    }
    private void purgeInvalid(){
        boolean changed=false;
        Iterator<Apartment> it=store.all.values().iterator();
        while(it.hasNext()){
            Apartment a=it.next();if(!sk.countryExists(a.country)){it.remove();changed=true;continue;}
            if(a.owner!=null&&!sk.member(a.country,a.owner)){a.owner=null;a.coowners.clear();a.publicBuild=false;a.publicInteract=false;a.rentPaidThrough=null;changed=true;}
            changed|=a.coowners.removeIf(u->!sk.member(a.country,u));
        }
        if(changed){store.save();refreshAllApartmentLabels();}
    }

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player p)){sender.sendMessage("Только для игроков.");return true;}
        if(args.length==0){if(command.getName().equals("apartments"))openList(p,1);else{Apartment a=store.owned(p.getUniqueId());if(a==null)openList(p,1);else openOwner(p,a);}return true;}
        String sub=args[0].toLowerCase(Locale.ROOT);
        switch(sub){
            case "list","список"->openList(p,1);
            case "home","дом"->home(p);
            case "menu","меню"->{Apartment a=store.owned(p.getUniqueId());if(a==null)p.sendMessage("§cУ вас нет квартиры.");else openOwner(p,a);}
            case "wand","палка"->giveWand(p);
            case "trust","add","добавить"->{if(args.length<2)p.sendMessage("§e/apartment trust <игрок>");else trust(p,args[1],true);}
            case "untrust","remove","удалить"->{if(args.length<2)p.sendMessage("§e/apartment untrust <игрок>");else trust(p,args[1],false);}
            default->p.sendMessage("§e/apartment home §7— телепортироваться, §e/apartment menu §7— моя квартира, §e/apartments §7— список.");
        }return true;
    }
    private void home(Player p){
        Apartment a=store.owned(p.getUniqueId());if(a==null){p.sendMessage("§cУ вас нет квартиры.");return;}
        if(a.spawn==null||!a.contains(a.spawn)){p.sendMessage("§cВ квартире ещё не установлена точка телепортации.");return;}
        UUID playerId=p.getUniqueId();
        if(homeTeleportTasks.containsKey(playerId)){p.sendMessage("§eТелепортация в квартиру уже ожидается.");return;}

        p.sendMessage("§eТелепортация в квартиру «"+a.name+"» через §f5 секунд§e. Урон отменит телепортацию.");
        final int[] seconds={5};
        BukkitTask task=Bukkit.getScheduler().runTaskTimer(this,()->{
            if(!p.isOnline()){
                BukkitTask pending=homeTeleportTasks.remove(playerId);if(pending!=null)pending.cancel();
                return;
            }
            if(seconds[0]>0){
                p.sendActionBar(Component.text("В квартиру через "+seconds[0]+" сек.",NamedTextColor.YELLOW));
                p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_HAT,0.5f,1.2f);
                seconds[0]--;
                return;
            }

            BukkitTask pending=homeTeleportTasks.remove(playerId);if(pending!=null)pending.cancel();
            Apartment current=store.owned(playerId);
            if(current==null||!current.id.equals(a.id)||current.spawn==null||!current.contains(current.spawn)){
                p.sendMessage("§cТелепортация отменена: квартира или точка телепортации больше недоступна.");
                return;
            }

            String tpl=getConfig().getString("teleport-command");Location l=current.spawn;String world=l.getWorld().getKey().asString();
            String cmd=tpl.replace("%world%",world).replace("%player%",p.getName()).replace("%x%",decimal(l.getX())).replace("%y%",decimal(l.getY())).replace("%z%",decimal(l.getZ())).replace("%yaw%",decimal(l.getYaw())).replace("%pitch%",decimal(l.getPitch()));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),cmd);
            p.sendMessage("§aВы телепортированы в квартиру «"+current.name+"».");
            p.playSound(p.getLocation(),Sound.ENTITY_ENDERMAN_TELEPORT,1f,1f);
        },0L,20L);
        homeTeleportTasks.put(playerId,task);
    }
    private void trust(Player p,String name,boolean add){
        Apartment a=store.owned(p.getUniqueId());if(a==null){p.sendMessage("§cУ вас нет квартиры.");return;}
        OfflinePlayer target=Bukkit.getOfflinePlayer(name);if(!target.hasPlayedBefore()&&!target.isOnline()){p.sendMessage("§cИгрок не найден.");return;}
        if(target.getUniqueId().equals(p.getUniqueId())){p.sendMessage("§cВы уже владелец.");return;}
        if(!sk.member(a.country,target.getUniqueId())){p.sendMessage("§cСовладелец должен состоять в той же стране.");return;}
        if(add){a.coowners.add(target.getUniqueId());p.sendMessage("§a"+target.getName()+" теперь совладелец квартиры.");}
        else{a.coowners.remove(target.getUniqueId());p.sendMessage("§e"+target.getName()+" удалён из совладельцев.");}
        store.save();
    }

    private record Menu(String type,String apartment,int page) implements InventoryHolder{public Inventory getInventory(){return null;}}
    private record Prompt(String type,String apartment){}
    private static final class Selection{Location left,right;}
    private ItemStack item(Material mat,String name,String...lore){
        ItemStack out=new ItemStack(mat);ItemMeta meta=out.getItemMeta();meta.displayName(Component.text(name));
        if(lore.length>0)meta.lore(Arrays.stream(lore).map(Component::text).toList());out.setItemMeta(meta);return out;
    }
    private Inventory menu(String type,String id,int page,int rows,String title){return Bukkit.createInventory(new Menu(type,id,page),rows*9,Component.text(title));}
    private void fill(Inventory inv){ItemStack pane=item(Material.GRAY_STAINED_GLASS_PANE," ");for(int i=0;i<inv.getSize();i++)inv.setItem(i,pane);}
    private String offer(Apartment a){return switch(a.offer){case CLOSED->"Закрыта";case SALE->"Продажа: "+money(a.price);case RENT->"Аренда: "+money(a.price)+"/день";};}
    private Location apartmentCenter(Apartment a){
        World world=Bukkit.getWorld(a.world);if(world==null)return null;
        double x=(a.minX+a.maxX+1)/2.0,y=(a.minY+a.maxY+1)/2.0,z=(a.minZ+a.maxZ+1)/2.0;
        return new Location(world,x,y,z);
    }
    private Component apartmentLabelText(Apartment a){
        Component title=Component.text(a.name,NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
        Component status;
        if(a.owner!=null){
            String owner=Optional.ofNullable(Bukkit.getOfflinePlayer(a.owner).getName()).orElse("Владелец");
            if(a.offer==Apartment.Offer.RENT)status=Component.text("Арендует: "+owner+" • "+money(a.price)+" монет/день",NamedTextColor.AQUA);
            else status=Component.text("Владелец: "+owner,NamedTextColor.GRAY);
        }else if(a.offer==Apartment.Offer.SALE){
            status=Component.text("Продажа • "+money(a.price)+" монет",NamedTextColor.GREEN);
        }else if(a.offer==Apartment.Offer.RENT){
            status=Component.text("Аренда • "+money(a.price)+" монет/день",NamedTextColor.AQUA);
        }else{
            status=Component.text("Не выставлена",NamedTextColor.GRAY);
        }
        return title.append(Component.newline()).append(status);
    }
    private void removeApartmentLabel(String apartmentId){
        UUID uuid=apartmentLabels.remove(apartmentId);
        if(uuid==null)return;
        Entity entity=Bukkit.getEntity(uuid);if(entity!=null)entity.remove();
    }
    private void removeAllApartmentLabels(){
        for(UUID uuid:new ArrayList<>(apartmentLabels.values())){Entity entity=Bukkit.getEntity(uuid);if(entity!=null)entity.remove();}
        apartmentLabels.clear();
    }
    private void refreshApartmentLabel(Apartment a){
        removeApartmentLabel(a.id);
        Location center=apartmentCenter(a);if(center==null)return;
        TextDisplay display=center.getWorld().spawn(center,TextDisplay.class,d->{
            d.text(apartmentLabelText(a));
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setShadowed(true);
            d.setSeeThrough(true);
            d.setLineWidth(240);
            d.setPersistent(false);
            d.getPersistentDataContainer().set(apartmentLabelKey,PersistentDataType.STRING,a.id);
        });
        apartmentLabels.put(a.id,display.getUniqueId());
    }
    private void refreshAllApartmentLabels(){
        removeAllApartmentLabels();
        for(Apartment a:store.all.values())refreshApartmentLabel(a);
    }
    private void openList(Player p,int page){
        String country=sk.country(p);if(country.isBlank()){p.sendMessage("§cСначала вступите в страну.");return;}
        List<Apartment> list=store.country(country);int pages=Math.max(1,(list.size()+27)/28);page=Math.max(1,Math.min(page,pages));
        Inventory inv=menu("list","",page,6,"Квартиры — "+sk.countryName(country));fill(inv);int[] slots={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        int start=(page-1)*28;for(int i=0;i<slots.length&&start+i<list.size();i++){Apartment a=list.get(start+i);String owner=a.owner==null?"Свободна":Optional.ofNullable(Bukkit.getOfflinePlayer(a.owner).getName()).orElse("Владелец");inv.setItem(slots[i],item(a.owner==null?Material.OAK_DOOR:Material.IRON_DOOR,a.name,"Статус: "+offer(a),"Владелец: "+owner,"Объём: "+a.volume()+" блоков","Нажмите для просмотра"));}
        Apartment mine=store.owned(p.getUniqueId());inv.setItem(45,item(mine==null?Material.GRAY_BED:Material.LIME_BED,"Моя квартира",mine==null?"Вы ещё не купили квартиру":"Открыть управление"));
        inv.setItem(48,item(Material.ARROW,"Предыдущая страница"));inv.setItem(49,item(Material.BOOK,"Страница "+page+" / "+pages));inv.setItem(50,item(Material.ARROW,"Следующая страница"));
        if(sk.manages(p))inv.setItem(53,item(Material.BLAZE_ROD,"Создать квартиру","Получить палку выделения","ЛКМ — первая точка","ПКМ — вторая точка"));p.openInventory(inv);
    }
    private void openPreview(Player p,Apartment a){
        Inventory inv=menu("preview",a.id,1,3,"Квартира: "+a.name);fill(inv);inv.setItem(4,item(Material.OAK_DOOR,a.name,"Статус: "+offer(a),"Размер: "+(a.maxX-a.minX+1)+"×"+(a.maxY-a.minY+1)+"×"+(a.maxZ-a.minZ+1)));
        if(a.owner==null&&a.offer!=Apartment.Offer.CLOSED)inv.setItem(13,item(Material.GOLD_INGOT,a.offer==Apartment.Offer.SALE?"Купить за "+money(a.price):"Арендовать за "+money(a.price)+"/день","Сумма поступит в казну страны","У каждого игрока может быть одна квартира"));
        else inv.setItem(13,item(Material.BARRIER,"Недоступно",a.owner!=null?"У квартиры уже есть владелец":"Квартира не выставлена"));
        if(sk.manages(p)&&sk.country(p).equals(a.country))inv.setItem(15,item(Material.COMPARATOR,"Управление квартирой"));inv.setItem(22,item(Material.ARROW,"Назад"));p.openInventory(inv);
    }
    private void openManager(Player p,Apartment a){
        if(!canManage(p,a)){p.sendMessage("§cНет права управлять этой квартирой.");return;}
        Inventory inv=menu("manager",a.id,1,4,"Настройка квартиры");fill(inv);
        inv.setItem(4,item(Material.OAK_DOOR,a.name,"ID: "+a.id,"Область: "+a.minX+","+a.minY+","+a.minZ+" — "+a.maxX+","+a.maxY+","+a.maxZ));
        inv.setItem(10,item(Material.NAME_TAG,"Изменить название","Сейчас: "+a.name));inv.setItem(12,item(Material.EMERALD,"Режим: "+offer(a),"Нажмите: закрыта → продажа → аренда"));
        inv.setItem(14,item(Material.GOLD_INGOT,"Изменить стоимость","Сейчас: "+money(a.price)));inv.setItem(16,item(Material.PLAYER_HEAD,"Запретить покупку ролям","Изначально разрешено всем","Запрещено ролей: "+a.blockedRoles.size()));
        inv.setItem(20,item(Material.LODESTONE,"Установить точку квартиры","Стойте внутри выделенной области"));inv.setItem(22,item(a.publicInteract?Material.LIME_DYE:Material.GRAY_DYE,"Общий доступ к сундукам и дверям: "+yes(a.publicInteract),"Владелец сможет изменить после покупки"));
        inv.setItem(24,item(a.publicBuild?Material.LIME_DYE:Material.GRAY_DYE,"Общее строительство: "+yes(a.publicBuild),"По умолчанию запрещено"));inv.setItem(30,item(Material.TNT,"Удалить квартиру","SHIFT + ЛКМ для удаления без возврата денег"));inv.setItem(31,item(Material.ARROW,"Назад"));p.openInventory(inv);
    }
    private void openOwner(Player p,Apartment a){
        Inventory inv=menu("owner",a.id,1,4,"Моя квартира: "+a.name);fill(inv);inv.setItem(4,item(Material.LIME_BED,a.name,"Страна: "+sk.countryName(a.country),a.offer==Apartment.Offer.RENT?"Аренда: "+money(a.price)+"/день":"Квартира куплена"));
        inv.setItem(11,item(Material.ENDER_PEARL,"Телепортироваться","Через 5 секунд","Урон отменяет телепортацию","Команда: /apartment home"));inv.setItem(13,item(Material.LODESTONE,"Установить точку телепортации","Нужно стоять внутри квартиры"));
        inv.setItem(15,item(Material.PLAYER_HEAD,"Совладельцы: "+a.coowners.size(),"/apartment trust <игрок>","/apartment untrust <игрок>","Совладельцы могут строить и открывать всё"));
        inv.setItem(20,item(a.publicInteract?Material.LIME_DYE:Material.GRAY_DYE,"Все могут открывать сундуки и двери: "+yes(a.publicInteract),"По умолчанию доступ только владельцу"));
        inv.setItem(24,item(a.publicBuild?Material.LIME_DYE:Material.GRAY_DYE,"Все могут строить: "+yes(a.publicBuild),"По умолчанию доступ только владельцу"));inv.setItem(31,item(Material.ARROW,"К списку квартир"));p.openInventory(inv);
    }
    private void openRoles(Player p,Apartment a){
        Inventory inv=menu("roles",a.id,1,6,"Роли без доступа к покупке");fill(inv);int slot=10;
        for(var e:sk.roles(a.country).entrySet()){while(slot%9==8||slot%9==0)slot++;if(slot>=44)break;boolean blocked=a.blockedRoles.contains(e.getKey());ItemStack it=item(blocked?Material.RED_DYE:Material.LIME_DYE,e.getValue(),blocked?"Покупка запрещена":"Покупка разрешена");ItemMeta m=it.getItemMeta();m.getPersistentDataContainer().set(new NamespacedKey(this,"role"),PersistentDataType.STRING,e.getKey());it.setItemMeta(m);inv.setItem(slot++,it);}
        inv.setItem(49,item(Material.ARROW,"Назад"));p.openInventory(inv);
    }
    private boolean canManage(Player p,Apartment a){return sk.country(p).equals(a.country)&&sk.manages(p);}
    private static String yes(boolean v){return v?"ДА":"НЕТ";}
    private static String money(double v){return String.format(Locale.US,"%.2f",v).replaceAll("\\.00$","");}
    private static String decimal(double v){return String.format(Locale.US,"%.3f",v);}

    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof Menu menu)||!(e.getWhoClicked() instanceof Player p))return;e.setCancelled(true);
        if(e.getRawSlot()<0||e.getRawSlot()>=e.getView().getTopInventory().getSize())return;int slot=e.getRawSlot();Apartment a=menu.apartment().isBlank()?null:store.all.get(menu.apartment());
        switch(menu.type()){
            case "list"->{
                if(slot==45){Apartment mine=store.owned(p.getUniqueId());if(mine==null)p.sendMessage("§eУ вас пока нет квартиры.");else openOwner(p,mine);return;}
                if(slot==48){openList(p,menu.page()-1);return;}if(slot==50){openList(p,menu.page()+1);return;}if(slot==53&&sk.manages(p)){p.closeInventory();giveWand(p);return;}
                List<Apartment> list=store.country(sk.country(p));int[] slots={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
                for(int i=0;i<slots.length;i++)if(slot==slots[i]){int index=(menu.page()-1)*28+i;if(index<list.size())openPreview(p,list.get(index));return;}
            }
            case "preview"->{if(a==null){openList(p,1);return;}if(slot==22){openList(p,1);return;}if(slot==15&&canManage(p,a)){openManager(p,a);return;}if(slot==13)buy(p,a);}
            case "manager"->{
                if(a==null||!canManage(p,a)){p.closeInventory();return;}if(slot==10){prompt(p,"name",a,"Введите новое название (1–"+maxName+" символов) или «отмена»:");return;}
                if(slot==12){a.offer=switch(a.offer){case CLOSED->Apartment.Offer.SALE;case SALE->Apartment.Offer.RENT;case RENT->Apartment.Offer.CLOSED;};store.save();refreshApartmentLabel(a);openManager(p,a);return;}
                if(slot==14){prompt(p,"price",a,"Введите стоимость от 0 до "+money(maxPrice)+" или «отмена»:");return;}if(slot==16){openRoles(p,a);return;}
                if(slot==20){setSpawn(p,a);openManager(p,a);return;}if(slot==22){a.publicInteract=!a.publicInteract;store.save();openManager(p,a);return;}if(slot==24){a.publicBuild=!a.publicBuild;store.save();openManager(p,a);return;}
                if(slot==30&&e.isShiftClick()&&e.isLeftClick()){removeApartmentLabel(a.id);store.all.remove(a.id);store.save();p.closeInventory();p.sendMessage("§eКвартира удалена без возврата денег.");return;}if(slot==31){openList(p,1);}
            }
            case "owner"->{
                if(a==null||!p.getUniqueId().equals(a.owner)){p.closeInventory();return;}if(slot==11){p.closeInventory();home(p);return;}if(slot==13){setSpawn(p,a);openOwner(p,a);return;}
                if(slot==15){p.closeInventory();p.sendMessage("§eДобавить: /apartment trust <игрок>§7. Удалить: §e/apartment untrust <игрок>§7.");return;}
                if(slot==20){a.publicInteract=!a.publicInteract;store.save();openOwner(p,a);return;}if(slot==24){a.publicBuild=!a.publicBuild;store.save();openOwner(p,a);return;}if(slot==31)openList(p,1);
            }
            case "roles"->{
                if(a==null||!canManage(p,a)){p.closeInventory();return;}if(slot==49){openManager(p,a);return;}ItemStack current=e.getCurrentItem();if(current==null)return;
                String role=current.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(this,"role"),PersistentDataType.STRING);if(role==null)return;
                if(a.blockedRoles.remove(role)){}else a.blockedRoles.add(role);store.save();openRoles(p,a);
            }
        }
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
    private void prompt(Player p,String type,Apartment a,String message){prompts.put(p.getUniqueId(),new Prompt(type,a.id));p.closeInventory();p.sendMessage("§e"+message);}
    @EventHandler(priority=EventPriority.LOWEST) public void chat(AsyncPlayerChatEvent e){
        Prompt prompt=prompts.remove(e.getPlayer().getUniqueId());if(prompt==null)return;e.setCancelled(true);String input=e.getMessage().trim();
        Bukkit.getScheduler().runTask(this,()->handlePrompt(e.getPlayer(),prompt,input));
    }
    private void handlePrompt(Player p,Prompt prompt,String input){
        Apartment a=store.all.get(prompt.apartment());if(a==null||!canManage(p,a)){p.sendMessage("§cКвартира больше недоступна.");return;}if(input.equalsIgnoreCase("отмена")){p.sendMessage("§eИзменение отменено.");openManager(p,a);return;}
        if(prompt.type().equals("name")){String clean=input.replaceAll("[\\p{Cntrl}§]","").trim();if(clean.isEmpty()||clean.length()>maxName){p.sendMessage("§cНазвание должно содержать от 1 до "+maxName+" символов.");openManager(p,a);return;}a.name=clean;}
        else{try{double value=Double.parseDouble(input.replace(',','.'));if(!Double.isFinite(value)||value<0||value>maxPrice)throw new NumberFormatException();a.price=Math.round(value*100.0)/100.0;}catch(NumberFormatException ex){p.sendMessage("§cВведите число от 0 до "+money(maxPrice)+".");openManager(p,a);return;}}
        store.save();refreshApartmentLabel(a);p.sendMessage("§aНастройка сохранена.");openManager(p,a);
    }
    private void buy(Player p,Apartment a){
        if(a.owner!=null||a.offer==Apartment.Offer.CLOSED){p.sendMessage("§cКвартира недоступна.");return;}if(!sk.country(p).equals(a.country)){p.sendMessage("§cКвартира доступна только гражданам этой страны.");return;}
        if(store.owned(p.getUniqueId())!=null){p.sendMessage("§cУ вас уже есть квартира.");return;}if(a.blockedRoles.contains(sk.role(a.country,p.getUniqueId()))){p.sendMessage("§cВашей роли запрещено покупать эту квартиру.");return;}
        double balance=sk.balance(p.getUniqueId());if(balance+0.00001<a.price){p.sendMessage("§cНедостаточно денег. Нужно "+money(a.price)+" монет.");return;}
        sk.balance(p.getUniqueId(),balance-a.price);sk.treasury(a.country,sk.treasury(a.country)+a.price);a.owner=p.getUniqueId();a.coowners.clear();a.publicBuild=false;a.publicInteract=false;a.rentPaidThrough=a.offer==Apartment.Offer.RENT?LocalDate.now(zone):null;store.save();refreshApartmentLabel(a);
        p.sendMessage(a.offer==Apartment.Offer.RENT?"§aВы арендовали квартиру «"+a.name+"». Следующая плата — в 00:00 МСК.":"§aВы купили квартиру «"+a.name+"».");openOwner(p,a);
    }
    private void setSpawn(Player p,Apartment a){if(!a.contains(p.getLocation())){p.sendMessage("§cТочка должна находиться внутри квартиры.");return;}a.spawn=p.getLocation().clone();store.save();p.sendMessage("§aТочка квартиры установлена.");}

    private void giveWand(Player p){
        if(!sk.manages(p)){p.sendMessage("§cУ вашей роли нет права «Управлять квартирами».");return;}ItemStack wand=item(Material.STICK,"§6Палка выделения квартиры","§eЛКМ §7— первая точка","§eПКМ §7— вторая точка","§7После второй точки палка исчезнет");ItemMeta meta=wand.getItemMeta();meta.getPersistentDataContainer().set(wandKey,PersistentDataType.BYTE,(byte)1);wand.setItemMeta(meta);p.getInventory().addItem(wand);selections.put(p.getUniqueId(),new Selection());p.sendMessage("§aПалка выдана. Выделите два противоположных угла внутри территории вашей страны.");
    }
    private boolean isWand(ItemStack item){return item!=null&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(wandKey,PersistentDataType.BYTE);}
    @EventHandler(priority=EventPriority.LOWEST) public void wand(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||!isWand(e.getItem())||e.getClickedBlock()==null)return;Action action=e.getAction();if(action!=Action.LEFT_CLICK_BLOCK&&action!=Action.RIGHT_CLICK_BLOCK)return;e.setCancelled(true);Player p=e.getPlayer();
        if(!sk.manages(p)){p.sendMessage("§cПраво управления квартирами было отозвано.");removeWand(p);return;}Selection s=selections.computeIfAbsent(p.getUniqueId(),u->new Selection());Location point=e.getClickedBlock().getLocation();
        if(action==Action.LEFT_CLICK_BLOCK){s.left=point;p.sendMessage("§eПервая точка: "+coords(point));return;}s.right=point;p.sendMessage("§eВторая точка: "+coords(point));if(s.left==null){p.sendMessage("§cСначала выберите первую точку ЛКМ.");return;}createFromSelection(p,s);
    }
    private void createFromSelection(Player p,Selection s){
        Location one=s.left,two=s.right;if(one.getWorld()!=two.getWorld()){p.sendMessage("§cТочки должны быть в одном мире.");return;}String country=sk.country(p);
        Apartment a=new Apartment(UUID.randomUUID().toString());a.country=country;a.name="Новая квартира";a.world=one.getWorld().getName();a.minX=Math.min(one.getBlockX(),two.getBlockX());a.maxX=Math.max(one.getBlockX(),two.getBlockX());a.minY=Math.min(one.getBlockY(),two.getBlockY());a.maxY=Math.max(one.getBlockY(),two.getBlockY());a.minZ=Math.min(one.getBlockZ(),two.getBlockZ());a.maxZ=Math.max(one.getBlockZ(),two.getBlockZ());
        if(a.maxX-a.minX+1>maxSide||a.maxY-a.minY+1>maxSide||a.maxZ-a.minZ+1>maxSide||a.volume()>maxVolume){p.sendMessage("§cОбласть слишком большая. Максимум: сторона "+maxSide+", объём "+maxVolume+" блоков.");return;}
        int minChunkX=Math.floorDiv(a.minX,16),maxChunkX=Math.floorDiv(a.maxX,16),minChunkZ=Math.floorDiv(a.minZ,16),maxChunkZ=Math.floorDiv(a.maxZ,16);
        for(int cx=minChunkX;cx<=maxChunkX;cx++)for(int cz=minChunkZ;cz<=maxChunkZ;cz++)if(!sk.chunkOwner(a.world,cx*16,cz*16).equals(country)){p.sendMessage("§cВся квартира должна находиться в занятых чанках вашей страны.");return;}
        for(Apartment other:store.all.values())if(a.overlaps(other)){p.sendMessage("§cОбласть пересекается с квартирой «"+other.name+"».");return;}
        store.all.put(a.id,a);store.save();refreshApartmentLabel(a);removeWand(p);selections.remove(p.getUniqueId());p.sendMessage("§aКвартира создана. Настройте название, режим и стоимость.");openManager(p,a);
    }
    private void removeWand(Player p){for(int i=0;i<p.getInventory().getSize();i++)if(isWand(p.getInventory().getItem(i)))p.getInventory().setItem(i,null);}
    private static String coords(Location l){return l.getBlockX()+", "+l.getBlockY()+", "+l.getBlockZ();}

    private boolean canBuild(Player p,Apartment a){return sk.manages(p)&&sk.country(p).equals(a.country)||a.resident(p.getUniqueId())||a.publicBuild;}
    private boolean canInteract(Player p,Apartment a){return sk.manages(p)&&sk.country(p).equals(a.country)||a.resident(p.getUniqueId())||a.publicInteract||a.publicBuild;}
    private void temporaryBypass(Player p,String permission){PermissionAttachment attachment=p.addAttachment(this,permission,true);Bukkit.getScheduler().runTask(this,attachment::remove);}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){Apartment a=store.at(e.getBlock().getLocation());if(a==null)return;if(canBuild(e.getPlayer(),a))temporaryBypass(e.getPlayer(),"politaria.apartment.event.build");else deny(e,e.getPlayer(),"строить");}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void placeBlock(BlockPlaceEvent e){Apartment a=store.at(e.getBlock().getLocation());if(a==null)return;if(canBuild(e.getPlayer(),a))temporaryBypass(e.getPlayer(),"politaria.apartment.event.build");else deny(e,e.getPlayer(),"строить");}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void bucketEmpty(PlayerBucketEmptyEvent e){protectBuild(e,e.getPlayer(),e.getBlock().getLocation());}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void bucketFill(PlayerBucketFillEvent e){protectBuild(e,e.getPlayer(),e.getBlock().getLocation());}
    private void protectBuild(Cancellable event,Player p,Location l){Apartment a=store.at(l);if(a==null)return;if(canBuild(p,a))temporaryBypass(p,"politaria.apartment.event.build");else deny(event,p,"строить");}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void use(PlayerInteractEvent e){if(e.getClickedBlock()==null||isWand(e.getItem()))return;Apartment a=store.at(e.getClickedBlock().getLocation());if(a==null)return;if(canInteract(e.getPlayer(),a))temporaryBypass(e.getPlayer(),"politaria.apartment.event.interact");else deny(e,e.getPlayer(),"открывать и использовать предметы");}
    private void deny(Cancellable e,Player p,String action){e.setCancelled(true);p.sendActionBar(Component.text("§cВ этой квартире вам запрещено "+action+"."));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explode(EntityExplodeEvent e){e.blockList().removeIf(b->store.at(b.getLocation())!=null);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void blockExplode(BlockExplodeEvent e){e.blockList().removeIf(b->store.at(b.getLocation())!=null);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void flow(BlockFromToEvent e){Apartment from=store.at(e.getBlock().getLocation()),to=store.at(e.getToBlock().getLocation());if(from!=to&&(from!=null||to!=null))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void mobChange(EntityChangeBlockEvent e){if(store.at(e.getBlock().getLocation())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void burn(BlockBurnEvent e){if(store.at(e.getBlock().getLocation())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void ignite(BlockIgniteEvent e){if(store.at(e.getBlock().getLocation())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void entityUse(PlayerInteractEntityEvent e){Apartment a=store.at(e.getRightClicked().getLocation());if(a==null)return;if(canInteract(e.getPlayer(),a))temporaryBypass(e.getPlayer(),"politaria.apartment.event.interact");else deny(e,e.getPlayer(),"использовать сущности");}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void piston(BlockPistonExtendEvent e){for(Block b:e.getBlocks()){if(store.at(b.getLocation())!=store.at(b.getRelative(e.getDirection()).getLocation())){e.setCancelled(true);return;}}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pistonBack(BlockPistonRetractEvent e){for(Block b:e.getBlocks()){if(store.at(b.getLocation())!=store.at(b.getRelative(e.getDirection().getOppositeFace()).getLocation())){e.setCancelled(true);return;}}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleportDamage(EntityDamageEvent e){
        if(!(e.getEntity() instanceof Player p))return;
        BukkitTask task=homeTeleportTasks.remove(p.getUniqueId());if(task==null)return;
        task.cancel();
        p.sendMessage("§cТелепортация в квартиру отменена — вы получили урон.");
        p.sendActionBar(Component.text("Телепортация в квартиру отменена",NamedTextColor.RED));
        p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,1f,0.7f);
    }
    @EventHandler public void quit(PlayerQuitEvent e){
        UUID id=e.getPlayer().getUniqueId();prompts.remove(id);selections.remove(id);
        BukkitTask task=homeTeleportTasks.remove(id);if(task!=null)task.cancel();
    }
}
