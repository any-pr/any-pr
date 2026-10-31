#Basic Loader
replaceitem entity @a slot.hotbar 0 snowball 1 0 {"item_lock":{"mode":"lock_in_slot"},"keep_on_death":{}}
execute at @e[type=snowball] run execute as @p run dialogue open @e[type=npc, c=1] @s first_page_default
kill @e[type=snowball]
kill @e[type=snow_golem]

#Bank Loader
scoreboard objectives add balance dummy
scoreboard players add @a balance 0
tag @a[scores={balance=..-1},tag=!banned] add banned
execute as @a[tag=!bypass_gm_check] run execute as @s[m=!default] run tag @s[m=!survival,tag=!banned] add banned

#Engineer Loader
#tag cmdcorp6534 add engineer
#tag @a[name=!cmdcorp6534] remove engineer

#DEV Loader
scoreboard objectives add correct_devkey dummy
scoreboard objectives add config_devkey dummy
scoreboard objectives add entering_devkey dummy
scoreboard players set devkey correct_devkey 1234
#scoreboard players add @a config_devkey 0
scoreboard players add @a entering_devkey 0