#!/bin/sh
# Writes DV New Crafts' JSON: blockstates, block and item models, loot tables, recipes and tags.
# The models lean on the game's own templates (torch, lantern, chain, bars) with copper textures in place.
# Run from the repo root:  sh tools/make-newcrafts-data.sh
set -e
R=dvnewcrafts/src/main/resources
A=$R/assets/dvnewcrafts
D=$R/data/dvnewcrafts
M=dvnewcrafts
mkdir -p $A/blockstates $A/models/block $A/models/item $A/particles $D/loot_tables/blocks $D/recipes \
    $R/data/minecraft/tags/items $R/data/minecraft/tags/blocks/mineable $R/data/forge/tags/items/nuggets

w() { cat > "$1"; }

# ---------- items
for item in sword shovel pickaxe axe hoe; do
    w $A/models/item/copper_$item.json <<EOF
{"parent": "minecraft:item/handheld", "textures": {"layer0": "$M:item/copper_$item"}}
EOF
done
for item in helmet chestplate leggings boots nugget; do
    w $A/models/item/copper_$item.json <<EOF
{"parent": "minecraft:item/generated", "textures": {"layer0": "$M:item/copper_$item"}}
EOF
done
w $A/models/item/copper_torch.json <<EOF
{"parent": "minecraft:item/generated", "textures": {"layer0": "$M:block/copper_torch"}}
EOF
w $A/models/item/copper_lantern.json <<EOF
{"parent": "minecraft:item/generated", "textures": {"layer0": "$M:item/copper_lantern"}}
EOF
w $A/models/item/copper_chain.json <<EOF
{"parent": "minecraft:item/generated", "textures": {"layer0": "$M:item/copper_chain"}}
EOF
w $A/models/item/copper_bars.json <<EOF
{"parent": "minecraft:item/generated", "textures": {"layer0": "$M:block/copper_bars"}}
EOF

# ---------- blocks
w $A/blockstates/copper_torch.json <<EOF
{"variants": {"": {"model": "$M:block/copper_torch"}}}
EOF
w $A/blockstates/copper_wall_torch.json <<EOF
{"variants": {
  "facing=east": {"model": "$M:block/copper_wall_torch"},
  "facing=north": {"model": "$M:block/copper_wall_torch", "y": 270},
  "facing=south": {"model": "$M:block/copper_wall_torch", "y": 90},
  "facing=west": {"model": "$M:block/copper_wall_torch", "y": 180}
}}
EOF
w $A/models/block/copper_torch.json <<EOF
{"parent": "minecraft:block/template_torch", "render_type": "minecraft:cutout", "textures": {"torch": "$M:block/copper_torch"}}
EOF
w $A/models/block/copper_wall_torch.json <<EOF
{"parent": "minecraft:block/template_torch_wall", "render_type": "minecraft:cutout", "textures": {"torch": "$M:block/copper_torch"}}
EOF

w $A/blockstates/copper_lantern.json <<EOF
{"variants": {
  "hanging=false": {"model": "$M:block/copper_lantern"},
  "hanging=true": {"model": "$M:block/copper_lantern_hanging"}
}}
EOF
w $A/models/block/copper_lantern.json <<EOF
{"parent": "minecraft:block/template_lantern", "render_type": "minecraft:cutout", "textures": {"lantern": "$M:block/copper_lantern"}}
EOF
w $A/models/block/copper_lantern_hanging.json <<EOF
{"parent": "minecraft:block/template_hanging_lantern", "render_type": "minecraft:cutout", "textures": {"lantern": "$M:block/copper_lantern"}}
EOF

w $A/blockstates/copper_chain.json <<EOF
{"variants": {
  "axis=x": {"model": "$M:block/copper_chain", "x": 90, "y": 90},
  "axis=y": {"model": "$M:block/copper_chain"},
  "axis=z": {"model": "$M:block/copper_chain", "x": 90}
}}
EOF
w $A/models/block/copper_chain.json <<EOF
{"parent": "minecraft:block/chain", "render_type": "minecraft:cutout_mipped", "textures": {"particle": "$M:block/copper_chain", "all": "$M:block/copper_chain"}}
EOF

# Bars: the game's multipart, pointed at copper copies of its six part models.
for part in post_ends post cap cap_alt side side_alt; do
    w $A/models/block/copper_bars_$part.json <<EOF
{"parent": "minecraft:block/iron_bars_$part", "render_type": "minecraft:cutout_mipped",
 "textures": {"particle": "$M:block/copper_bars", "bars": "$M:block/copper_bars", "edge": "$M:block/copper_bars"}}
EOF
done
B=$M:block/copper_bars
w $A/blockstates/copper_bars.json <<EOF
{"multipart": [
  {"apply": {"model": "${B}_post_ends"}},
  {"when": {"north": "false", "east": "false", "south": "false", "west": "false"}, "apply": {"model": "${B}_post"}},
  {"when": {"north": "true", "east": "false", "south": "false", "west": "false"}, "apply": {"model": "${B}_cap"}},
  {"when": {"north": "false", "east": "true", "south": "false", "west": "false"}, "apply": {"model": "${B}_cap", "y": 90}},
  {"when": {"north": "false", "east": "false", "south": "true", "west": "false"}, "apply": {"model": "${B}_cap_alt"}},
  {"when": {"north": "false", "east": "false", "south": "false", "west": "true"}, "apply": {"model": "${B}_cap_alt", "y": 90}},
  {"when": {"north": "true"}, "apply": {"model": "${B}_side"}},
  {"when": {"east": "true"}, "apply": {"model": "${B}_side", "y": 90}},
  {"when": {"south": "true"}, "apply": {"model": "${B}_side_alt"}},
  {"when": {"west": "true"}, "apply": {"model": "${B}_side_alt", "y": 90}}
]}
EOF

w $A/particles/copper_flame.json <<EOF
{"textures": ["$M:copper_flame"]}
EOF

# ---------- loot tables (the wall torch drops like the torch, see DVNewCrafts)
for block in copper_torch copper_lantern copper_chain copper_bars; do
    w $D/loot_tables/blocks/$block.json <<EOF
{"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
  "entries": [{"type": "minecraft:item", "name": "$M:$block"}],
  "conditions": [{"condition": "minecraft:survives_explosion"}]}]}
EOF
done

# ---------- recipes
# shaped <file> <result> <count> <pattern rows as one JSON array> <key JSON>
shaped() {
    w $D/recipes/$1.json <<EOF
{"type": "minecraft:crafting_shaped", "category": "$6", "pattern": $4, "key": $5, "result": {"item": "$2", "count": $3}}
EOF
}
I='{"item": "minecraft:copper_ingot"}'
S='{"item": "minecraft:stick"}'
shaped copper_sword $M:copper_sword 1 '["C", "C", "S"]' "{\"C\": $I, \"S\": $S}" equipment
shaped copper_shovel $M:copper_shovel 1 '["C", "S", "S"]' "{\"C\": $I, \"S\": $S}" equipment
shaped copper_pickaxe $M:copper_pickaxe 1 '["CCC", " S ", " S "]' "{\"C\": $I, \"S\": $S}" equipment
shaped copper_axe $M:copper_axe 1 '["CC", "CS", " S"]' "{\"C\": $I, \"S\": $S}" equipment
shaped copper_hoe $M:copper_hoe 1 '["CC", " S", " S"]' "{\"C\": $I, \"S\": $S}" equipment
shaped copper_helmet $M:copper_helmet 1 '["CCC", "C C"]' "{\"C\": $I}" equipment
shaped copper_chestplate $M:copper_chestplate 1 '["C C", "CCC", "CCC"]' "{\"C\": $I}" equipment
shaped copper_leggings $M:copper_leggings 1 '["CCC", "C C", "C C"]' "{\"C\": $I}" equipment
shaped copper_boots $M:copper_boots 1 '["C C", "C C"]' "{\"C\": $I}" equipment

N="{\"item\": \"$M:copper_nugget\"}"
w $D/recipes/copper_nugget.json <<EOF
{"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [$I], "result": {"item": "$M:copper_nugget", "count": 9}}
EOF
shaped copper_ingot_from_nuggets minecraft:copper_ingot 1 '["NNN", "NNN", "NNN"]' "{\"N\": $N}" misc
shaped copper_torch $M:copper_torch 4 '["N", "C", "S"]' "{\"N\": $N, \"C\": {\"tag\": \"minecraft:coals\"}, \"S\": $S}" misc
shaped copper_lantern $M:copper_lantern 1 '["NNN", "NTN", "NNN"]' "{\"N\": $N, \"T\": {\"item\": \"$M:copper_torch\"}}" misc
shaped copper_chain $M:copper_chain 1 '["N", "C", "N"]' "{\"N\": $N, \"C\": $I}" misc
shaped copper_bars $M:copper_bars 16 '["CCC", "CCC"]' "{\"C\": $I}" misc

# Worn-out copper gear melts back down to nuggets, like iron and gold gear.
GEAR="sword shovel pickaxe axe hoe helmet chestplate leggings boots"
INGREDIENTS=""
for g in $GEAR; do INGREDIENTS="$INGREDIENTS{\"item\": \"$M:copper_$g\"}, "; done
INGREDIENTS="[${INGREDIENTS%, }]"
w $D/recipes/copper_nugget_from_smelting.json <<EOF
{"type": "minecraft:smelting", "category": "misc", "ingredient": $INGREDIENTS, "result": "$M:copper_nugget", "experience": 0.1, "cookingtime": 200}
EOF
w $D/recipes/copper_nugget_from_blasting.json <<EOF
{"type": "minecraft:blasting", "category": "misc", "ingredient": $INGREDIENTS, "result": "$M:copper_nugget", "experience": 0.1, "cookingtime": 100}
EOF

# Things the game only hands out as loot.
L='{"item": "minecraft:leather"}'
shaped saddle minecraft:saddle 1 '["LLL", "LIL"]' "{\"L\": $L, \"I\": {\"item\": \"minecraft:iron_ingot\"}}" equipment
w $D/recipes/name_tag.json <<EOF
{"type": "minecraft:crafting_shapeless", "category": "misc",
 "ingredients": [{"item": "minecraft:paper"}, {"item": "minecraft:paper"}, {"item": "minecraft:string"}, {"item": "minecraft:iron_nugget"}],
 "result": {"item": "minecraft:name_tag"}}
EOF
W='{"tag": "minecraft:wool"}'
shaped iron_horse_armor minecraft:iron_horse_armor 1 '["M M", "MWM", "M M"]' "{\"M\": {\"item\": \"minecraft:iron_ingot\"}, \"W\": $W}" equipment
shaped golden_horse_armor minecraft:golden_horse_armor 1 '["M M", "MWM", "M M"]' "{\"M\": {\"item\": \"minecraft:gold_ingot\"}, \"W\": $W}" equipment
shaped diamond_horse_armor minecraft:diamond_horse_armor 1 '["M M", "MWM", "M M"]' "{\"M\": {\"item\": \"minecraft:diamond\"}, \"W\": $W}" equipment
C='{"item": "minecraft:chain"}'
shaped chainmail_helmet minecraft:chainmail_helmet 1 '["CCC", "C C"]' "{\"C\": $C}" equipment
shaped chainmail_chestplate minecraft:chainmail_chestplate 1 '["C C", "CCC", "CCC"]' "{\"C\": $C}" equipment
shaped chainmail_leggings minecraft:chainmail_leggings 1 '["CCC", "C C", "C C"]' "{\"C\": $C}" equipment
shaped chainmail_boots minecraft:chainmail_boots 1 '["C C", "C C"]' "{\"C\": $C}" equipment

# ---------- tags
list() { printf '{"replace": false, "values": ['; first=1; for v in "$@"; do [ $first = 1 ] || printf ', '; printf '"%s"' "$v"; first=0; done; printf ']}\n'; }
list $M:copper_sword > $R/data/minecraft/tags/items/swords.json
list $M:copper_shovel > $R/data/minecraft/tags/items/shovels.json
list $M:copper_pickaxe > $R/data/minecraft/tags/items/pickaxes.json
list $M:copper_axe > $R/data/minecraft/tags/items/axes.json
list $M:copper_hoe > $R/data/minecraft/tags/items/hoes.json
list $M:copper_helmet $M:copper_chestplate $M:copper_leggings $M:copper_boots > $R/data/minecraft/tags/items/trimmable_armor.json
list $M:copper_nugget > $R/data/forge/tags/items/nuggets/copper.json
list $M:copper_lantern $M:copper_chain $M:copper_bars > $R/data/minecraft/tags/blocks/mineable/pickaxe.json
echo "wrote DV New Crafts JSON"
