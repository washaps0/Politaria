from pathlib import Path

path = Path('server/pluginsource/PolitariaApartments/src/main/java/ru/politaria/apartments/ApartmentPlugin.java')
text = path.read_text(encoding='utf-8')


def replace_once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly 1 match, found {count}')
    text = text.replace(old, new, 1)

replace_once(
    'import org.bukkit.event.entity.EntityExplodeEvent;\nimport org.bukkit.event.entity.EntityChangeBlockEvent;\n',
    'import org.bukkit.event.entity.EntityExplodeEvent;\nimport org.bukkit.event.entity.EntityChangeBlockEvent;\nimport org.bukkit.event.entity.EntityDamageEvent;\n',
    'EntityDamageEvent import',
)

replace_once(
    '    private BukkitTask midnightTask;\n',
    '    private BukkitTask midnightTask;\n    private final Map<UUID,BukkitTask> homeTeleportTasks=new HashMap<>();\n',
    'home teleport task field',
)

replace_once(
    '    @Override public void onDisable(){if(midnightTask!=null)midnightTask.cancel();removeAllApartmentLabels();store.save();selections.clear();prompts.clear();}\n',
    '    @Override public void onDisable(){if(midnightTask!=null)midnightTask.cancel();for(BukkitTask task:homeTeleportTasks.values())task.cancel();homeTeleportTasks.clear();removeAllApartmentLabels();store.save();selections.clear();prompts.clear();}\n',
    'onDisable cleanup',
)

old_home = '''    private void home(Player p){
        Apartment a=store.owned(p.getUniqueId());if(a==null){p.sendMessage("§cУ вас нет квартиры.");return;}
        if(a.spawn==null||!a.contains(a.spawn)){p.sendMessage("§cВ квартире ещё не установлена точка телепортации.");return;}
        String tpl=getConfig().getString("teleport-command");Location l=a.spawn;String world=l.getWorld().getKey().asString();
        String cmd=tpl.replace("%world%",world).replace("%player%",p.getName()).replace("%x%",decimal(l.getX())).replace("%y%",decimal(l.getY())).replace("%z%",decimal(l.getZ())).replace("%yaw%",decimal(l.getYaw())).replace("%pitch%",decimal(l.getPitch()));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),cmd);p.sendMessage("§aВы телепортированы в квартиру «"+a.name+"».");
    }
'''

new_home = '''    private void home(Player p){
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
'''
replace_once(old_home, new_home, 'home teleport method')

replace_once(
    '        inv.setItem(11,item(Material.ENDER_PEARL,"Телепортироваться","Команда: /apartment home"));inv.setItem(13,item(Material.LODESTONE,"Установить точку телепортации","Нужно стоять внутри квартиры"));\n',
    '        inv.setItem(11,item(Material.ENDER_PEARL,"Телепортироваться","Через 5 секунд","Урон отменяет телепортацию","Команда: /apartment home"));inv.setItem(13,item(Material.LODESTONE,"Установить точку телепортации","Нужно стоять внутри квартиры"));\n',
    'owner menu teleport lore',
)

replace_once(
    '    @EventHandler public void quit(PlayerQuitEvent e){prompts.remove(e.getPlayer().getUniqueId());selections.remove(e.getPlayer().getUniqueId());}\n',
    '''    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleportDamage(EntityDamageEvent e){
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
''',
    'damage cancel and quit cleanup',
)

path.write_text(text, encoding='utf-8')
print('Apartment teleport delay patch applied.')
