execute as @e[type=npc] run execute at @s run tp @s ~ 32767 ~
execute as @a at @a run execute if entity @s[r=1] positioned ~-2 32767 ~-2 run tag @e[type=npc, dx=4, dz=4] add alive
kill @e[type=npc, tag=!alive]
tag @e[type=npc] remove alive
execute as @a at @s run execute positioned ~-2 32767 ~-2 run execute unless entity @e[type=npc, dx=4, dz=4] run summon npc ~2 ~ ~2