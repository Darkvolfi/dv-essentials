#!/bin/sh
# Writes DV Science of Souls' JSON: item and block models, blockstates, loot tables, recipes and tags.
# The Soul Altar is built from the game's own blackstone and soul textures, pointed at from its model.
# Run from the repo root:  sh tools/make-souls-data.sh
set -e
R=dvsouls/src/main/resources
A=$R/assets/dvsouls
D=$R/data/dvsouls
M=dvsouls
mkdir -p $A/models/item $A/models/block $A/blockstates $D/loot_tables/blocks $D/recipes $D/tags/entity_types \
    $R/data/minecraft/tags/blocks/mineable
w() { cat > "$1"; }

# ---------- items
w $A/models/item/soul_jar.json <<EOF
{"parent": "minecraft:item/generated", "textures": {"layer0": "$M:item/soul_jar"}}
EOF
w $A/models/item/filled_soul_jar.json <<EOF
{"parent": "minecraft:item/generated",
 "textures": {"layer0": "$M:item/soul_jar", "layer1": "$M:item/soul_jar_soul", "layer2": "$M:item/soul_jar_spots"}}
EOF
w $A/models/item/collar_shard.json <<EOF
{"parent": "minecraft:item/generated",
 "textures": {"layer0": "$M:item/collar_shard", "layer1": "$M:item/collar_shard_gem"}}
EOF
w $A/models/item/soul_collar.json <<EOF
{"parent": "minecraft:item/generated", "textures": {"layer0": "$M:item/soul_collar"}}
EOF
w $A/models/item/soul_altar.json <<EOF
{"parent": "$M:block/soul_altar"}
EOF

# ---------- the Soul Altar: a blackstone base, a carved pillar, and a top of soul soil (sculk once a copy lies on it)
altar() {
    w $A/models/block/$1.json <<EOF
{
  "parent": "minecraft:block/block",
  "textures": {
    "base": "minecraft:block/polished_blackstone",
    "pillar": "minecraft:block/chiseled_polished_blackstone",
    "rim": "minecraft:block/polished_blackstone_bricks",
    "top": "$2",
    "particle": "minecraft:block/polished_blackstone"
  },
  "elements": [
    {"from": [1, 0, 1], "to": [15, 4, 15], "faces": {
      "down": {"texture": "#base", "cullface": "down"}, "up": {"texture": "#base"},
      "north": {"texture": "#base"}, "south": {"texture": "#base"}, "west": {"texture": "#base"}, "east": {"texture": "#base"}}},
    {"from": [4, 4, 4], "to": [12, 10, 12], "faces": {
      "north": {"texture": "#pillar"}, "south": {"texture": "#pillar"}, "west": {"texture": "#pillar"}, "east": {"texture": "#pillar"}}},
    {"from": [2, 10, 2], "to": [14, 13, 14], "faces": {
      "down": {"texture": "#rim"}, "up": {"texture": "#top"},
      "north": {"texture": "#rim"}, "south": {"texture": "#rim"}, "west": {"texture": "#rim"}, "east": {"texture": "#rim"}}}
  ]
}
EOF
}
altar soul_altar minecraft:block/soul_soil
altar soul_altar_filled minecraft:block/sculk
w $A/blockstates/soul_altar.json <<EOF
{"variants": {"filled=false": {"model": "$M:block/soul_altar"}, "filled=true": {"model": "$M:block/soul_altar_filled"}}}
EOF
w $D/loot_tables/blocks/soul_altar.json <<EOF
{"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
  "entries": [{"type": "minecraft:item", "name": "$M:soul_altar"}],
  "conditions": [{"condition": "minecraft:survives_explosion"}]}]}
EOF
w $R/data/minecraft/tags/blocks/mineable/pickaxe.json <<EOF
{"replace": false, "values": ["$M:soul_altar", "$M:soul_furnace", "$M:soul_fuser"]}
EOF

# ---------- the Soul Furnace (a furnace in blackstone, burning soul fire) and the Soul Fuser (blackstone, with a
# respawn anchor's top that lights up while it works)
w $A/models/block/soul_furnace.json <<EOF
{"parent": "minecraft:block/orientable", "textures": {"top": "minecraft:block/polished_blackstone",
 "front": "$M:block/soul_furnace_front", "side": "minecraft:block/polished_blackstone_bricks"}}
EOF
w $A/models/block/soul_furnace_on.json <<EOF
{"parent": "minecraft:block/orientable", "textures": {"top": "minecraft:block/polished_blackstone",
 "front": "$M:block/soul_furnace_front_on", "side": "minecraft:block/polished_blackstone_bricks"}}
EOF
printf '{"variants": {' > $A/blockstates/soul_furnace.json
sep=""
for lit in false true; do
    model=$M:block/soul_furnace; [ $lit = true ] && model=${model}_on
    for f in "north 0" "east 90" "south 180" "west 270"; do
        set -- $f
        printf '%s
  "facing=%s,lit=%s": {"model": "%s", "y": %s}' "$sep" $1 $lit $model $2 >> $A/blockstates/soul_furnace.json
        sep=","
    done
done
printf '
}}
' >> $A/blockstates/soul_furnace.json
fuser() {
    w $A/models/block/$1.json <<EOF
{"parent": "minecraft:block/cube_bottom_top", "textures": {"top": "$2",
 "bottom": "minecraft:block/polished_blackstone", "side": "minecraft:block/chiseled_polished_blackstone"}}
EOF
}
fuser soul_fuser minecraft:block/respawn_anchor_top_off
fuser soul_fuser_on minecraft:block/respawn_anchor_top
w $A/blockstates/soul_fuser.json <<EOF
{"variants": {"lit=false": {"model": "$M:block/soul_fuser"}, "lit=true": {"model": "$M:block/soul_fuser_on"}}}
EOF
for b in soul_furnace soul_fuser; do
    w $A/models/item/$b.json <<EOF
{"parent": "$M:block/$b"}
EOF
    w $D/loot_tables/blocks/$b.json <<EOF
{"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
  "entries": [{"type": "minecraft:item", "name": "$M:$b"}],
  "conditions": [{"condition": "minecraft:survives_explosion"}]}]}
EOF
done

# ---------- recipes
w $D/recipes/soul_jar.json <<EOF
{"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["Q", "B", "S"],
 "key": {"Q": {"item": "minecraft:quartz"}, "B": {"item": "minecraft:glass_bottle"}, "S": {"tag": "minecraft:soul_fire_base_blocks"}},
 "result": {"item": "$M:soul_jar"}}
EOF
w $D/recipes/soul_collar.json <<EOF
{"type": "minecraft:crafting_shaped", "category": "misc", "pattern": [" L ", "L L", " J "],
 "key": {"L": {"item": "minecraft:leather"}, "J": {"item": "$M:soul_jar"}},
 "result": {"item": "$M:soul_collar"}}
EOF
w $D/recipes/soul_altar.json <<EOF
{"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["QJQ", "BCB", "BBB"],
 "key": {"Q": {"item": "minecraft:quartz"}, "J": {"item": "$M:soul_jar"}, "C": {"item": "minecraft:crying_obsidian"},
         "B": {"item": "minecraft:polished_blackstone"}},
 "result": {"item": "$M:soul_altar"}}
EOF

# ---------- hybrids: spawn eggs, and what they drop (the second creature's loot: a woolly cow drops a cow's)
mkdir -p $D/loot_tables/entities
for mob in woolly_cow feathered_pig lava_horse wolf_fox; do
    w $A/models/item/${mob}_spawn_egg.json <<EOF
{"parent": "minecraft:item/template_spawn_egg"}
EOF
done
# drop <item> <min> <max> [smelts]: one loot pool, looting adds up to one more; "smelts" cooks it when the creature burns.
drop() {
    smelt=""
    [ "$4" = smelts ] && smelt=', {"function": "minecraft:furnace_smelt", "conditions": [{"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"flags": {"is_on_fire": true}}}]}'
    printf '{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "%s", "functions": [{"function": "minecraft:set_count", "count": {"min": %s, "max": %s}}, {"function": "minecraft:looting_enchant", "count": {"min": 0, "max": 1}}%s]}]}' "$1" "$2" "$3" "$smelt"
}
loot() { f=$1; shift; printf '{"type": "minecraft:entity", "pools": [%s]}
' "$*" > $D/loot_tables/entities/$f.json; }
loot woolly_cow "$(drop minecraft:leather 0 2), $(drop minecraft:beef 1 3 smelts)"
loot feathered_pig "$(drop minecraft:porkchop 1 3 smelts), $(drop minecraft:feather 0 2)"
loot lava_horse "$(drop minecraft:leather 0 2), $(drop minecraft:magma_cream 0 1)"
loot wolf_fox "$(drop minecraft:leather 0 1)"

w $D/recipes/soul_furnace.json <<EOF
{"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["BBB", "BFB", "BJB"],
 "key": {"B": {"item": "minecraft:polished_blackstone"}, "F": {"item": "minecraft:furnace"}, "J": {"item": "$M:soul_jar"}},
 "result": {"item": "$M:soul_furnace"}}
EOF
w $D/recipes/soul_fuser.json <<EOF
{"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["QJQ", "OSO", "BBB"],
 "key": {"Q": {"item": "minecraft:quartz"}, "J": {"item": "$M:soul_jar"}, "O": {"item": "minecraft:crying_obsidian"},
         "S": {"item": "minecraft:brewing_stand"}, "B": {"item": "minecraft:polished_blackstone"}},
 "result": {"item": "$M:soul_fuser"}}
EOF

# ---------- creatures no jar or collar can hold
w $D/tags/entity_types/uncapturable.json <<EOF
{"replace": false, "values": [
  {"id": "#forge:bosses", "required": false},
  "minecraft:ender_dragon", "minecraft:wither", "minecraft:warden", "minecraft:elder_guardian"
]}
EOF
echo "wrote DV Science of Souls JSON"
