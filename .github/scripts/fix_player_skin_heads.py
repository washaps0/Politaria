from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)

# countries.sk — member list + member detail
countries_path = Path('server/plugins/Skript/scripts/countries.sk')
countries = countries_path.read_text(encoding='utf-8')

old_block = '''\t\tset {_member-profile} to offlineplayer({_member-uuid}, false)\n\t\tif {_member-profile} is set:\n\t\t\tset {_member-head} to head of {_member-profile}\n\t\telse:\n\t\t\tset {_member-head} to player head\n'''
new_block = '''\t\tset {_member-head} to player head\n\t\tset {_online-member} to player({_member-uuid}, true)\n\t\tif {_online-member} is set:\n\t\t\tset {_loaded-head} to head of {_online-member}\n\t\t\tif {_loaded-head} is set:\n\t\t\t\tset {_member-head} to {_loaded-head}\n\t\telse:\n\t\t\tset {_member-profile} to offlineplayer({_member-name}, true)\n\t\t\tif {_member-profile} is set:\n\t\t\t\tset {_loaded-head} to head of {_member-profile}\n\t\t\t\tif {_loaded-head} is set:\n\t\t\t\t\tset {_member-head} to {_loaded-head}\n'''
countries = replace_once(countries, old_block, new_block, 'country member list head')

old_detail = '''\tset {_member-profile} to offlineplayer({_memberUuid}, false)\n\tif {_member-profile} is set:\n\t\tset {_member-head} to head of {_member-profile}\n\telse:\n\t\tset {_member-head} to player head\n'''
new_detail = '''\tset {_member-head} to player head\n\tset {_online-member} to player({_memberUuid}, true)\n\tif {_online-member} is set:\n\t\tset {_loaded-head} to head of {_online-member}\n\t\tif {_loaded-head} is set:\n\t\t\tset {_member-head} to {_loaded-head}\n\telse:\n\t\tset {_member-profile} to offlineplayer({_name}, true)\n\t\tif {_member-profile} is set:\n\t\t\tset {_loaded-head} to head of {_member-profile}\n\t\t\tif {_loaded-head} is set:\n\t\t\t\tset {_member-head} to {_loaded-head}\n'''
countries = replace_once(countries, old_detail, new_detail, 'country member detail head')
countries_path.write_text(countries, encoding='utf-8')

# wanted.sk — one shared helper is used by wanted list/passport/detail
wanted_path = Path('server/plugins/Skript/scripts/wanted.sk')
wanted = wanted_path.read_text(encoding='utf-8')
old_helper = '''# Всегда возвращает видимый предмет. У офлайн-игрока это обычная player head,\n# у онлайн-игрока — его настоящая голова. Так GUI не получает пустой item.\nfunction policeWantedHead(targetUuid: text) :: item:\n\tset {_head} to player head\n\tset {_online} to player({_targetUuid}, true)\n\tif {_online} is set:\n\t\tset {_onlineHead} to head of {_online}\n\t\tif {_onlineHead} is set:\n\t\t\tif {_onlineHead} is not air:\n\t\t\t\tset {_head} to {_onlineHead}\n\treturn {_head}\n'''
new_helper = '''# Возвращает голову с настоящим скином.\n# Для игрока в сети берём профиль напрямую. Для офлайн-игрока делаем\n# lookup по сохранённому нику, потому что UUID сервера может быть offline UUID.\nfunction policeWantedHead(targetUuid: text) :: item:\n\tset {_head} to player head\n\tset {_online} to player({_targetUuid}, true)\n\tif {_online} is set:\n\t\tset {_onlineHead} to head of {_online}\n\t\tif {_onlineHead} is set:\n\t\t\tif {_onlineHead} is not air:\n\t\t\t\treturn {_onlineHead}\n\n\tset {_name} to policeTargetName({_targetUuid})\n\tif {_name} is not "Неизвестный игрок":\n\t\tset {_offline} to offlineplayer({_name}, true)\n\t\tif {_offline} is set:\n\t\t\tset {_offlineHead} to head of {_offline}\n\t\t\tif {_offlineHead} is set:\n\t\t\t\tif {_offlineHead} is not air:\n\t\t\t\t\treturn {_offlineHead}\n\treturn {_head}\n'''
wanted = replace_once(wanted, old_helper, new_helper, 'wanted head helper')
wanted_path.write_text(wanted, encoding='utf-8')

print('Player skin head fixes applied to countries.sk and wanted.sk')
