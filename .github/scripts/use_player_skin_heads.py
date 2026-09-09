from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly 1 match, found {count}')
    return text.replace(old, new, 1)

# ---------------------------------------------------------
# countries.sk — country member list + member detail
# ---------------------------------------------------------
countries_path = Path('server/plugins/Skript/scripts/countries.sk')
countries = countries_path.read_text(encoding='utf-8')

old_members = '''\t\tset {_slot} to {_slots::%{_position}%}\n\t\tset {_roles-text} to countryMemberRolesText({_id}, {_member-uuid})\n\t\tset slot {_slot} of {_menu} to paper named "<white><bold>%{_member-name}%" with lore "<gray>Роли: <white>%{_roles-text}%" and "" and "<yellow>Нажмите для управления"\n\t\tset {country.menu-member::%uuid of {_p}%::%{_slot}%} to {_member-uuid}\n'''
new_members = '''\t\tset {_slot} to {_slots::%{_position}%}\n\t\tset {_roles-text} to countryMemberRolesText({_id}, {_member-uuid})\n\t\tset {_member-profile} to offlineplayer({_member-uuid}, false)\n\t\tif {_member-profile} is set:\n\t\t\tset {_member-head} to head of {_member-profile}\n\t\telse:\n\t\t\tset {_member-head} to player head\n\t\tset name of {_member-head} to "<white><bold>%{_member-name}%"\n\t\tset lore of {_member-head} to "<gray>Роли: <white>%{_roles-text}%" and "" and "<yellow>Нажмите для управления"\n\t\tset slot {_slot} of {_menu} to {_member-head}\n\t\tset {country.menu-member::%uuid of {_p}%::%{_slot}%} to {_member-uuid}\n'''
countries = replace_once(countries, old_members, new_members, 'country member list')

old_member_detail = '''\tset slot 4 of {_menu} to player head named "<white><bold>%{_name}%" with lore "<gray>Роли: <white>%{_roles-text}%"\n'''
new_member_detail = '''\tset {_member-profile} to offlineplayer({_memberUuid}, false)\n\tif {_member-profile} is set:\n\t\tset {_member-head} to head of {_member-profile}\n\telse:\n\t\tset {_member-head} to player head\n\tset name of {_member-head} to "<white><bold>%{_name}%"\n\tset lore of {_member-head} to "<gray>Роли: <white>%{_roles-text}%"\n\tset slot 4 of {_menu} to {_member-head}\n'''
countries = replace_once(countries, old_member_detail, new_member_detail, 'country member detail')

countries_path.write_text(countries, encoding='utf-8')

# ---------------------------------------------------------
# wanted.sk — passport + wanted list + wanted detail
# ---------------------------------------------------------
wanted_path = Path('server/plugins/Skript/scripts/wanted.sk')
wanted = wanted_path.read_text(encoding='utf-8')

old_passport = '''\tset slot 4 of {_menu} to player head named "<aqua><bold>%{_name}%" with lore "<gray>Паспорт игрока" and "<dark gray>UUID: %{_targetUuid}%"\n'''
new_passport = '''\tif {_target} is set:\n\t\tset {_target-head} to head of {_target}\n\telse:\n\t\tset {_target-head} to player head\n\tset name of {_target-head} to "<aqua><bold>%{_name}%"\n\tset lore of {_target-head} to "<gray>Паспорт игрока" and "<dark gray>UUID: %{_targetUuid}%"\n\tset slot 4 of {_menu} to {_target-head}\n'''
wanted = replace_once(wanted, old_passport, new_passport, 'passport head')

old_wanted_list = '''\t\t\tset slot {_slot} of {_menu} to player head named "<red><bold>%{_name}%" with lore policeStars({_stars}) and "<gray>%policeLevelName({_stars})%" and "<gray>Гражданство: %{_citizenship}%" and "" and "<yellow>Нажмите для подробностей"\n\t\t\tset {police.menu-target::%uuid of {_p}%::%{_slot}%} to {_targetUuid}\n'''
new_wanted_list = '''\t\t\tset {_target-profile} to offlineplayer({_targetUuid}, false)\n\t\t\tif {_target-profile} is set:\n\t\t\t\tset {_target-head} to head of {_target-profile}\n\t\t\telse:\n\t\t\t\tset {_target-head} to player head\n\t\t\tset name of {_target-head} to "<red><bold>%{_name}%"\n\t\t\tset lore of {_target-head} to policeStars({_stars}) and "<gray>%policeLevelName({_stars})%" and "<gray>Гражданство: %{_citizenship}%" and "" and "<yellow>Нажмите для подробностей"\n\t\t\tset slot {_slot} of {_menu} to {_target-head}\n\t\t\tset {police.menu-target::%uuid of {_p}%::%{_slot}%} to {_targetUuid}\n'''
wanted = replace_once(wanted, old_wanted_list, new_wanted_list, 'wanted list head')

old_wanted_detail = '''\tset slot 4 of {_menu} to player head named "<red><bold>%{_name}%" with lore policeStars({_stars}) and "<gray>%policeLevelName({_stars})%" and "<gray>Гражданство: %{_citizenship}%"\n'''
new_wanted_detail = '''\tset {_target-profile} to offlineplayer({_targetUuid}, false)\n\tif {_target-profile} is set:\n\t\tset {_target-head} to head of {_target-profile}\n\telse:\n\t\tset {_target-head} to player head\n\tset name of {_target-head} to "<red><bold>%{_name}%"\n\tset lore of {_target-head} to policeStars({_stars}) and "<gray>%policeLevelName({_stars})%" and "<gray>Гражданство: %{_citizenship}%"\n\tset slot 4 of {_menu} to {_target-head}\n'''
wanted = replace_once(wanted, old_wanted_detail, new_wanted_detail, 'wanted detail head')

wanted_path.write_text(wanted, encoding='utf-8')

print('Player skin heads added to country members, passport and wanted GUIs.')
