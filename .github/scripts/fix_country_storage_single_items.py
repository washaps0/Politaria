from pathlib import Path

p = Path('server/plugins/Skript/scripts/countries_storage.sk')
s = p.read_text(encoding='utf-8')

old_func = '''function countryStoragePutItemInCategory(id: text, category: text, itemToStore: item) :: boolean:
\tset {_free} to countryStorageFindFreeGlobalSlot({_id})
\tif {_free} is less than 0:
\t\treturn false
\tset {country.storage::%{_id}%::%{_free}%} to {_itemToStore}
\tif {_category} is "general":
\t\tdelete {country.storage-category::%{_id}%::%{_free}%}
\telse:
\t\tset {country.storage-category::%{_id}%::%{_free}%} to {_category}
\tset {_type-text} to "%type of {_itemToStore}%"
\tset {country.storage-category-type::%{_id}%::%{_category}%::%{_type-text}%} to true
\treturn true
'''

new_func = old_func + '''
function countryStoragePutOneInCategory(id: text, category: text, itemToStore: item) :: boolean:
\tset {_one} to {_itemToStore}
\tset amount of {_one} to 1
\tset {_max} to countryStorageMaxSlots({_id}) - 1
\tloop integers between 0 and {_max}:
\t\tset {_stored} to {country.storage::%{_id}%::%loop-value%}
\t\tif {_stored} is set:
\t\t\tset {_stored-category} to countryStorageCategoryOfSlot({_id}, loop-value)
\t\t\tif {_stored-category} is {_category}:
\t\t\t\tset {_stored-one} to {_stored}
\t\t\t\tset amount of {_stored-one} to 1
\t\t\t\tif {_stored-one} is {_one}:
\t\t\t\t\tset {_stored-type} to type of {_stored}
\t\t\t\t\tset {_limit} to maximum stack size of {_stored-type}
\t\t\t\t\tset {_current} to amount of {_stored}
\t\t\t\t\tif {_current} is less than {_limit}:
\t\t\t\t\t\tadd 1 to {_current}
\t\t\t\t\t\tset amount of {_stored} to {_current}
\t\t\t\t\t\tset {country.storage::%{_id}%::%loop-value%} to {_stored}
\t\t\t\t\t\tset {_type-text} to "%type of {_one}%"
\t\t\t\t\t\tset {country.storage-category-type::%{_id}%::%{_category}%::%{_type-text}%} to true
\t\t\t\t\t\treturn true
\treturn countryStoragePutItemInCategory({_id}, {_category}, {_one})
'''

if old_func not in s:
    raise SystemExit('countryStoragePutItemInCategory block not found')
s = s.replace(old_func, new_func, 1)

old_lore = '''\tset slot 49 of {_menu} to barrel named "<gold><bold>%{_name}%" with lore "<gray>Предметов-стаков: <white>%{_count}%" and "<gray>Общий лимит склада не меняется." and "" and "<gray>Клик по предмету снизу — положить стак." and "<gray>Клик по предмету сверху — забрать стак."
'''
new_lore = '''\tset slot 49 of {_menu} to barrel named "<gold><bold>%{_name}%" with lore "<gray>Предметов-стаков: <white>%{_count}%" and "<gray>Общий лимит склада не меняется." and "" and "<gray>ЛКМ снизу — положить весь стак." and "<gray>ПКМ снизу — положить 1 предмет." and "<gray>ЛКМ сверху — забрать весь стак." and "<gray>ПКМ сверху — забрать 1 предмет."
'''
if old_lore not in s:
    raise SystemExit('storage category lore block not found')
s = s.replace(old_lore, new_lore, 1)

old_take = '''\t\tif {_raw} is between 0 and 44:
\t\t\tif countryStorageCanWithdraw(player) is not true:
\t\t\t\tsend action bar "<red>У вашей роли нет права забирать вещи из хранилища." to player
\t\t\t\tstop
\t\t\tset {_global} to {country.storage-view::%uuid of player%::%{_raw}%}
\t\t\tif {_global} is not set:
\t\t\t\tstop
\t\t\tset {_stored} to {country.storage::%{_id}%::%{_global}%}
\t\t\tif {_stored} is not set:
\t\t\t\tstop
\t\t\tgive {_stored} to player
\t\t\tdelete {country.storage::%{_id}%::%{_global}%}
\t\t\tdelete {country.storage-category::%{_id}%::%{_global}%}
\t\t\tcountryOpenStorageCategory(player, {_cat}, {_page})
\t\t\tstop
'''

new_take = '''\t\tif {_raw} is between 0 and 44:
\t\t\tif countryStorageCanWithdraw(player) is not true:
\t\t\t\tsend action bar "<red>У вашей роли нет права забирать вещи из хранилища." to player
\t\t\t\tstop
\t\t\tset {_global} to {country.storage-view::%uuid of player%::%{_raw}%}
\t\t\tif {_global} is not set:
\t\t\t\tstop
\t\t\tset {_stored} to {country.storage::%{_id}%::%{_global}%}
\t\t\tif {_stored} is not set:
\t\t\t\tstop
\t\t\tif click type is right mouse button or click type is right mouse button with shift:
\t\t\t\tset {_one} to {_stored}
\t\t\t\tset amount of {_one} to 1
\t\t\t\tgive {_one} to player
\t\t\t\tset {_amount} to amount of {_stored}
\t\t\t\tif {_amount} is less than or equal to 1:
\t\t\t\t\tdelete {country.storage::%{_id}%::%{_global}%}
\t\t\t\t\tdelete {country.storage-category::%{_id}%::%{_global}%}
\t\t\t\telse:
\t\t\t\t\tsubtract 1 from {_amount}
\t\t\t\t\tset amount of {_stored} to {_amount}
\t\t\t\t\tset {country.storage::%{_id}%::%{_global}%} to {_stored}
\t\t\telse:
\t\t\t\tgive {_stored} to player
\t\t\t\tdelete {country.storage::%{_id}%::%{_global}%}
\t\t\t\tdelete {country.storage-category::%{_id}%::%{_global}%}
\t\t\tcountryOpenStorageCategory(player, {_cat}, {_page})
\t\t\tstop
'''
if old_take not in s:
    raise SystemExit('storage withdraw block not found')
s = s.replace(old_take, new_take, 1)

old_put = '''\t\tif {_raw} is greater than or equal to 54:
\t\t\tif countryStorageCanDeposit(player) is not true:
\t\t\t\tsend action bar "<red>У вашей роли нет права пополнять хранилище." to player
\t\t\t\tstop
\t\t\tset {_item} to event-item
\t\t\tif {_item} is not set:
\t\t\t\tstop
\t\t\tif {_item} is air:
\t\t\t\tstop
\t\t\tif countryStoragePutItemInCategory({_id}, {_cat}, {_item}) is not true:
\t\t\t\tsend action bar "<red>Хранилище заполнено." to player
\t\t\t\tstop
\t\t\tset event-slot to air
\t\t\tcountryOpenStorageCategory(player, {_cat}, {_page})
\t\t\tstop
'''

new_put = '''\t\tif {_raw} is greater than or equal to 54:
\t\t\tif countryStorageCanDeposit(player) is not true:
\t\t\t\tsend action bar "<red>У вашей роли нет права пополнять хранилище." to player
\t\t\t\tstop
\t\t\tset {_item} to event-item
\t\t\tif {_item} is not set:
\t\t\t\tstop
\t\t\tif {_item} is air:
\t\t\t\tstop
\t\t\tif click type is right mouse button or click type is right mouse button with shift:
\t\t\t\tif countryStoragePutOneInCategory({_id}, {_cat}, {_item}) is not true:
\t\t\t\t\tsend action bar "<red>Хранилище заполнено." to player
\t\t\t\t\tstop
\t\t\t\tset {_remaining} to {_item}
\t\t\t\tset {_amount} to amount of {_remaining}
\t\t\t\tif {_amount} is less than or equal to 1:
\t\t\t\t\tset event-slot to air
\t\t\t\telse:
\t\t\t\t\tsubtract 1 from {_amount}
\t\t\t\t\tset amount of {_remaining} to {_amount}
\t\t\t\t\tset event-slot to {_remaining}
\t\t\telse:
\t\t\t\tif countryStoragePutItemInCategory({_id}, {_cat}, {_item}) is not true:
\t\t\t\t\tsend action bar "<red>Хранилище заполнено." to player
\t\t\t\t\tstop
\t\t\t\tset event-slot to air
\t\t\tcountryOpenStorageCategory(player, {_cat}, {_page})
\t\t\tstop
'''
if old_put not in s:
    raise SystemExit('storage deposit block not found')
s = s.replace(old_put, new_put, 1)

p.write_text(s, encoding='utf-8')
