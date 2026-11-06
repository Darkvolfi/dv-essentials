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
# The Binding Altar: the same shape, its top a lodestone's, glowing with a respawn anchor's light once a soul lies on it.
altar binding_altar minecraft:block/lodestone_top
altar binding_altar_filled minecraft:block/respawn_anchor_top
w $A/blockstates/binding_altar.json <<EOF
{"variants": {"filled=false": {"model": "$M:block/binding_altar"}, "filled=true": {"model": "$M:block/binding_altar_filled"}}}
EOF
w $A/blockstates/soul_altar.json <<EOF
{"variants": {"filled=false": {"model": "$M:block/soul_altar"}, "filled=true": {"model": "$M:block/soul_altar_filled"}}}
EOF
w $D/loot_tables/blocks/soul_altar.json <<EOF
{"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
  "entries": [{"type": "minecraft:item", "name": "$M:soul_altar"}],
  "conditions": [{"condition": "minecraft:survives_explosion"}]}]}
EOF
w $R/data/minecraft/tags/blocks/mineable/pickaxe.json <<EOF
{"replace": false, "values": ["$M:soul_altar", "$M:binding_altar", "$M:soul_furnace", "$M:soul_smoker", "$M:soul_blast_furnace", "$M:soul_fuser", "$M:soul_distiller", "$M:soul_refiner"]}
EOF

# ---------- the Soul Furnace, Soul Smoker and Soul Blast Furnace (the game's ones in blackstone, burning soul fire, with a
# gold inlay framing their sides and top like the Refiner's); the Soul Fuser's model is with the Refiner's, below
for b in soul_furnace soul_smoker soul_blast_furnace; do
    for on in "" _on; do
        w $A/models/block/$b$on.json <<EOF
{"parent": "minecraft:block/orientable", "textures": {"top": "minecraft:block/polished_blackstone",
 "front": "$M:block/${b}_front$on", "side": "minecraft:block/polished_blackstone_bricks", "inlay": "$M:block/furnace_inlay"},
 "render_type": "minecraft:cutout", "elements": [
  {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
   "down": {"texture": "#top", "cullface": "down"}, "up": {"texture": "#top", "cullface": "up"},
   "north": {"texture": "#front", "cullface": "north"}, "south": {"texture": "#side", "cullface": "south"},
   "west": {"texture": "#side", "cullface": "west"}, "east": {"texture": "#side", "cullface": "east"}}},
  {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
   "up": {"texture": "#inlay", "cullface": "up"}, "south": {"texture": "#inlay", "cullface": "south"},
   "west": {"texture": "#inlay", "cullface": "west"}, "east": {"texture": "#inlay", "cullface": "east"}}}]}
EOF
    done
    printf '{"variants": {' > $A/blockstates/$b.json
    sep=""
    for lit in false true; do
        model=$M:block/$b; [ $lit = true ] && model=${model}_on
        for f in "north 0" "east 90" "south 180" "west 270"; do
            set -- $f
            printf '%s\n  "facing=%s,lit=%s": {"model": "%s", "y": %s}' "$sep" $1 $lit $model $2 >> $A/blockstates/$b.json
            sep=","
        done
    done
    printf '\n}}\n' >> $A/blockstates/$b.json
done
for b in soul_furnace soul_smoker soul_blast_furnace soul_fuser binding_altar; do
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
loot woolly_cow_woolly "$(drop minecraft:leather 0 2), $(drop minecraft:beef 1 3 smelts), $(drop minecraft:white_wool 1 1)"
loot feathered_pig "$(drop minecraft:porkchop 1 3 smelts), $(drop minecraft:feather 0 2)"
loot lava_horse "$(drop minecraft:leather 0 2), $(drop minecraft:magma_cream 0 1)"
loot wolf_fox "$(drop minecraft:leather 0 1)"

w $D/recipes/soul_furnace.json <<EOF
{"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["BBB", "BFB", "BJB"],
 "key": {"B": {"item": "minecraft:polished_blackstone"}, "F": {"item": "minecraft:furnace"}, "J": {"item": "$M:soul_jar"}},
 "result": {"item": "$M:soul_furnace"}}
EOF
# The smoker and blast furnace are built round a Soul Furnace, like the game's are round a furnace.
w $D/recipes/soul_smoker.json <<EOF
{"type": "minecraft:crafting_shaped", "category": "misc", "pattern": [" L ", "LFL", " L "],
 "key": {"L": {"tag": "minecraft:logs"}, "F": {"item": "$M:soul_furnace"}},
 "result": {"item": "$M:soul_smoker"}}
EOF
w $D/recipes/soul_blast_furnace.json <<EOF
{"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["III", "IFI", "BBB"],
 "key": {"I": {"item": "minecraft:iron_ingot"}, "F": {"item": "$M:soul_furnace"}, "B": {"item": "minecraft:polished_blackstone"}},
 "result": {"item": "$M:soul_blast_furnace"}}
EOF
# DV Backpack cards of the three (registered only with DV Backpack, so their recipes ask for it): a card base and the block.
for b in soul_furnace soul_smoker soul_blast_furnace; do
    w $A/models/item/${b}_upgrade.json <<EOF
{"parent": "dvbackpack:item/upgrade_card", "textures": {"face": "$M:block/${b}_front"}}
EOF
    w $D/recipes/${b}_upgrade.json <<EOF
{"type": "minecraft:crafting_shapeless", "category": "misc",
 "conditions": [{"type": "forge:mod_loaded", "modid": "dvbackpack"}],
 "ingredients": [{"item": "dvbackpack:upgrade_base"}, {"item": "$M:$b"}],
 "result": {"item": "$M:${b}_upgrade"}}
EOF
done
w $D/recipes/binding_altar.json <<EOF
{"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["QJQ", "LSL", "BBB"],
 "key": {"Q": {"item": "minecraft:quartz"}, "J": {"item": "$M:soul_jar"}, "L": {"item": "minecraft:lead"},
         "S": {"item": "minecraft:soul_lantern"}, "B": {"item": "minecraft:polished_blackstone"}},
 "result": {"item": "$M:binding_altar"}}
EOF
w $D/recipes/soul_fuser.json <<EOF
{"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["QJQ", "OSO", "BBB"],
 "key": {"Q": {"item": "minecraft:quartz"}, "J": {"item": "$M:soul_jar"}, "O": {"item": "minecraft:crying_obsidian"},
         "S": {"item": "minecraft:brewing_stand"}, "B": {"item": "minecraft:polished_blackstone"}},
 "result": {"item": "$M:soul_fuser"}}
EOF


# ---------- soul charms: the Soul Distiller (its model is with the Refiner's, below), essences, charms
w $A/models/item/soul_distiller.json <<EOF2
{"parent": "$M:block/soul_distiller"}
EOF2
w $D/loot_tables/blocks/soul_distiller.json <<EOF2
{"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
  "entries": [{"type": "minecraft:item", "name": "$M:soul_distiller"}],
  "conditions": [{"condition": "minecraft:survives_explosion"}]}]}
EOF2
w $A/models/item/soul_essence.json <<EOF2
{"parent": "minecraft:item/generated", "textures": {"layer0": "$M:item/soul_essence", "layer1": "$M:item/soul_essence_glow"}}
EOF2
# Soul cards (the soul charms): a card like DV Backpack's upgrade cards, a frame in the tier's metal round a dark rack
# with 1, 2 or 3 test tubes standing in it. Each tube's soul is tinted by its essence (tint i for the i-th essence;
# an empty place is dark), its glass and stopper drawn over it.
card_tube() { # x of the tube's left edge, its tint index
    printf ',\n  {"from": [%s, 4, 8.6], "to": [%s, 11, 8.7], "faces": {"south": {"uv": [6, 1, 10, 15], "texture": "#fill", "tintindex": %s}, "north": {"uv": [10, 1, 6, 15], "texture": "#fill", "tintindex": %s}}}' $1 $(( $1 + 2 )) $2 $2
    printf ',\n  {"from": [%s, 4, 8.55], "to": [%s, 11, 8.75], "faces": {"south": {"uv": [6, 1, 10, 15], "texture": "#tube"}, "north": {"uv": [10, 1, 6, 15], "texture": "#tube"}}}' $1 $(( $1 + 2 ))
}
for tier in "gold 7" "diamond 5 9" "netherite 4 7 10"; do
    set -- $tier
    name=$1; shift
    tubes=""; i=0
    for x in "$@"; do tubes="$tubes$(card_tube $x $i)"; i=$(( i + 1 )); done
    w $A/models/item/${name}_soul_charm.json <<EOF2
{"gui_light": "front", "render_type": "minecraft:cutout",
 "textures": {"frame": "$M:item/soul_card_$name", "face": "$M:item/soul_card_face", "tube": "$M:item/soul_essence",
  "fill": "$M:item/soul_essence_glow", "particle": "$M:item/soul_card_$name"},
 "elements": [
  {"from": [0, 0, 7.5], "to": [16, 16, 8.5], "faces": {
   "south": {"uv": [0, 0, 16, 16], "texture": "#frame"}, "north": {"uv": [16, 0, 0, 16], "texture": "#frame"},
   "up": {"uv": [0, 0, 16, 1], "texture": "#frame"}, "down": {"uv": [0, 15, 16, 16], "texture": "#frame"},
   "east": {"uv": [15, 0, 16, 16], "texture": "#frame"}, "west": {"uv": [0, 0, 1, 16], "texture": "#frame"}}},
  {"from": [3, 3, 7.45], "to": [13, 13, 8.55], "faces": {
   "south": {"uv": [0, 0, 16, 16], "texture": "#face"}, "north": {"uv": [16, 0, 0, 16], "texture": "#face"}}}$tubes
 ],
 "display": {
  "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
  "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
  "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
  "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
  "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]}}}
EOF2
done
w $A/models/item/soul_tube.json <<EOF2
{"parent": "minecraft:item/generated", "textures": {"layer0": "$M:item/soul_tube"}}
EOF2
# Two glass and a quartz stopper make four test tubes.
w $D/recipes/soul_tube.json <<EOF2
{"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["Q", "G", "G"],
 "key": {"Q": {"item": "minecraft:quartz"}, "G": {"item": "minecraft:glass"}},
 "result": {"item": "$M:soul_tube", "count": 4}}
EOF2
w $D/recipes/soul_distiller.json <<EOF2
{"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["QJQ", "ACA", "BBB"],
 "key": {"Q": {"item": "minecraft:quartz"}, "J": {"item": "$M:soul_jar"}, "A": {"item": "minecraft:amethyst_block"},
         "C": {"item": "minecraft:cauldron"}, "B": {"item": "minecraft:polished_blackstone"}},
 "result": {"item": "$M:soul_distiller"}}
EOF2
w $D/recipes/gold_soul_charm.json <<EOF2
{"type": "minecraft:crafting_shaped", "category": "misc", "pattern": [" P ", "GAG", " G "],
 "key": {"P": {"item": "minecraft:paper"}, "G": {"item": "minecraft:gold_ingot"}, "A": {"item": "minecraft:amethyst_shard"}},
 "result": {"item": "$M:gold_soul_charm"}}
EOF2
# The next tiers keep the charm's essences: diamonds round a gold charm, then netherite at the smithing table.
w $D/recipes/diamond_soul_charm.json <<EOF2
{"type": "$M:charm_upgrade", "category": "misc", "pattern": [" D ", "DCD", " D "],
 "key": {"D": {"item": "minecraft:diamond"}, "C": {"item": "$M:gold_soul_charm"}},
 "result": {"item": "$M:diamond_soul_charm"}}
EOF2
w $D/recipes/netherite_soul_charm.json <<EOF2
{"type": "minecraft:smithing_transform", "template": {"item": "minecraft:netherite_upgrade_smithing_template"},
 "base": {"item": "$M:diamond_soul_charm"}, "addition": {"item": "minecraft:netherite_ingot"},
 "result": {"item": "$M:netherite_soul_charm"}}
EOF2
# Without DV Backpack essences go on in the crafting grid; with it, in the charm's panel in the backpack.
w $D/recipes/charm_essences.json <<EOF2
{"type": "$M:charm_essences", "category": "misc",
 "conditions": [{"type": "forge:not", "value": {"type": "forge:mod_loaded", "modid": "dvbackpack"}}]}
EOF2
# DV Backpack takes items with this tag into its upgrade slots (a tag of another mod's: harmless without it).
mkdir -p $R/data/dvbackpack/tags/items
w $R/data/dvbackpack/tags/items/upgrades.json <<EOF2
{"replace": false, "values": ["$M:gold_soul_charm", "$M:diamond_soul_charm", "$M:netherite_soul_charm"]}
EOF2

# ---------- pure soul: the liquid (its block shows only particles), a bucket and a bottle of it, soul coal, and the Soul
# Refiner (blackstone round a sculk catalyst's top, which blooms while it works)
w $A/blockstates/pure_soul.json <<EOF
{"variants": {"": {"model": "$M:block/pure_soul"}}}
EOF
w $A/models/block/pure_soul.json <<EOF
{"textures": {"particle": "$M:block/pure_soul_still"}}
EOF
w $A/models/item/pure_soul_bottle.json <<EOF
{"parent": "minecraft:item/generated", "textures": {"layer0": "$M:item/pure_soul_bottle"}}
EOF
# the game's bucket and coal under our liquid and veins
w $A/models/item/pure_soul_bucket.json <<EOF
{"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/bucket", "layer1": "$M:item/pure_soul_bucket"}}
EOF
w $A/models/item/soul_coal.json <<EOF
{"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/coal", "layer1": "$M:item/soul_coal"}}
EOF
# The Soul Refiner, seen from its front (north; the model's x runs right to left from there): a blackstone slab with a
# wall behind, on the left a box (the jar standing under it, its neck plugged in, is drawn by the block entity renderer
# as the item itself); a bar of amethyst from the box's plug to the plug of a tower on the right, whose framed window
# shows how full the bar of souls is. Parts come and go with the block state: the amethyst (cracking as it wears), souls
# flowing along it while it works, and the window's fill.
el() { # x1 y1 z1 x2 y2 z2 texture [faces]
    faces=${8:-north east south west up down}; out=""
    for f in $faces; do out="$out${out:+, }\"$f\": {\"texture\": \"#$7\"}"; done
    printf '{"from": [%s, %s, %s], "to": [%s, %s, %s], "faces": {%s}}' $1 $2 $3 $4 $5 $6 "$out"
}
# Stone, all worked: the slab, tower and plugs polished, the wall bricks, the box chiseled. Gold: the socket
# the jar's neck plugs into under the box, the clamps holding the jar on its pad, and the rings round the amethyst's ends.
REFINER_BODY="$(el 0 0 0 16 5 16 polished), $(el 0 5 12 16 16 16 bricks), $(el 10 11 4 16 16 12 chiseled),
 $(el 9 12 7 10 14 9 polished), $(el 4 12 7 5 14 9 polished), $(el 0 5 4 4 16 12 polished),
 $(el 0.5 5.5 3.9 3.5 15.5 4 frame north), $(el 1 6 3.85 3 15 3.9 window north),
 $(el 11 10 6.5 14 11 9.5 gold), $(el 9.5 5 5.5 15.5 5.5 10.5 polished),
 $(el 10 5.5 7.5 10.5 7.5 8.5 gold), $(el 14.5 5.5 7.5 15 7.5 8.5 gold),
 $(el 8.5 11.5 6.5 9 14.5 9.5 gold), $(el 5 11.5 6.5 5.5 14.5 9.5 gold)"
# The gold inlays, a hair outside the stone they lie on, each face reading its part of the texture as the stone does.
uv() { # texture face u1 v1 u2 v2 ...
    t=$1; shift; out=""
    while [ $# -gt 0 ]; do out="$out${out:+, }\"$1\": {\"uv\": [$2, $3, $4, $5], \"texture\": \"#$t\"}"; shift 5; done
    printf '%s' "$out"
}
REFINER_INLAY="{\"from\": [-0.01, -0.01, -0.01], \"to\": [16.01, 5.01, 16.01], \"faces\": {$(uv band north 0 11 16 16 south 0 11 16 16 east 0 11 16 16 west 0 11 16 16)}},
 {\"from\": [9.99, 10.99, 3.99], \"to\": [16.01, 16.01, 12.01], \"faces\": {$(uv box north 0 0 6 5 east 4 0 12 5 west 4 0 12 5)}},
 {\"from\": [-0.01, 4.99, 3.99], \"to\": [4.01, 16.01, 12.01], \"faces\": {$(uv tower east 4 0 12 11 west 4 0 12 11)}},
 {\"from\": [-0.01, 4.99, 11.99], \"to\": [16.01, 16.01, 16.01], \"faces\": {$(uv wall north 0 0 16 11)}}"
REFINER_INLAY_TEXTURES="\"band\": \"$M:block/refiner_band\", \"box\": \"$M:block/refiner_box\", \"tower\": \"$M:block/refiner_tower\", \"wall\": \"$M:block/refiner_wall\""
REFINER_AMETHYST="$(el 5 12 7 9 14 9 amethyst)"
w $A/models/block/soul_refiner.json <<EOF
{"parent": "minecraft:block/block", "textures": {"particle": "minecraft:block/polished_blackstone",
 "frame": "minecraft:block/smooth_stone", "window": "minecraft:block/black_concrete",
 "polished": "minecraft:block/polished_blackstone", "bricks": "minecraft:block/polished_blackstone_bricks",
 "chiseled": "minecraft:block/chiseled_polished_blackstone", "gold": "minecraft:block/gold_block", $REFINER_INLAY_TEXTURES},
 "render_type": "minecraft:cutout", "elements": [$REFINER_BODY, $REFINER_INLAY]}
EOF
# whole, then our cracks over it as it wears (the game's destroy_stage textures turn grey outside block breaking)
for crack in 1 2 3 4; do
    over=""
    [ $crack -gt 1 ] && over=", $(el 4.99 11.99 6.99 9.01 14.01 9.01 crack)"
    stage=$(( crack > 1 ? crack - 1 : 1 ))
    w $A/models/block/soul_refiner_amethyst_$crack.json <<EOF
{"textures": {"particle": "minecraft:block/amethyst_block", "amethyst": "minecraft:block/amethyst_block",
 "crack": "$M:block/amethyst_crack_$stage"}, "render_type": "minecraft:cutout", "elements": [$REFINER_AMETHYST$over]}
EOF
done
# souls flowing towards the tower: uv flipped where a face's u runs the other way
w $A/models/block/soul_refiner_flow.json <<EOF
{"textures": {"particle": "$M:block/soul_flow", "flow": "$M:block/soul_flow"}, "render_type": "minecraft:translucent",
 "elements": [{"from": [4.98, 11.98, 6.98], "to": [9.02, 14.02, 9.02], "shade": false, "faces": {
   "north": {"uv": [7, 2, 11, 4], "texture": "#flow"}, "south": {"uv": [9, 2, 5, 4], "texture": "#flow"},
   "up": {"uv": [9, 7, 5, 9], "texture": "#flow"}, "down": {"uv": [9, 7, 5, 9], "texture": "#flow"}}}]}
EOF
for level in 1 2 3 4 5 6 7 8; do
    top=$(( 6 + level * 9 / 8 ))
    w $A/models/block/soul_refiner_level_$level.json <<EOF
{"textures": {"particle": "$M:block/pure_soul_still", "soul": "$M:block/pure_soul_still"}, "render_type": "minecraft:translucent",
 "elements": [$(el 1 6 3.8 3 $top 3.85 soul north)]}
EOF
done
{
    printf '{"multipart": ['
    sep=""
    for f in "north 0" "east 90" "south 180" "west 270"; do
        set -- $f
        part() { printf '%s\n  {"when": {%s}, "apply": {"model": "%s", "y": %s}}' "$sep" "$1" "$2" "$3"; sep=","; }
        part "\"facing\": \"$1\"" $M:block/soul_refiner $2
        part "\"facing\": \"$1\", \"lit\": \"true\"" $M:block/soul_refiner_flow $2
        for crack in 1 2 3 4; do part "\"facing\": \"$1\", \"crack\": \"$crack\"" $M:block/soul_refiner_amethyst_$crack $2; done
        for level in 1 2 3 4 5 6 7 8; do part "\"facing\": \"$1\", \"level\": \"$level\"" $M:block/soul_refiner_level_$level $2; done
    done
    printf '\n]}\n'
} > $A/blockstates/soul_refiner.json
w $A/models/item/soul_refiner.json <<EOF
{"parent": "minecraft:block/block", "textures": {"particle": "minecraft:block/polished_blackstone",
 "frame": "minecraft:block/smooth_stone", "window": "minecraft:block/black_concrete",
 "polished": "minecraft:block/polished_blackstone", "bricks": "minecraft:block/polished_blackstone_bricks",
 "chiseled": "minecraft:block/chiseled_polished_blackstone", "gold": "minecraft:block/gold_block", "soul": "$M:block/pure_soul_still",
 $REFINER_INLAY_TEXTURES,
 "amethyst": "minecraft:block/amethyst_block"},
 "render_type": "minecraft:cutout", "elements": [$REFINER_BODY, $REFINER_INLAY, $REFINER_AMETHYST, $(el 1 6 3.8 3 10 3.85 soul north)]}
EOF
# ---------- the Soul Fuser and the Soul Distiller, in the Refiner's worked blackstone and gold; their jars, the
# Distiller's amethyst and what they make are drawn by the renderer. Each shows its bar of souls in a window.
MACHINE_TEXTURES="\"particle\": \"minecraft:block/polished_blackstone\", \"polished\": \"minecraft:block/polished_blackstone\",
 \"bricks\": \"minecraft:block/polished_blackstone_bricks\", \"chiseled\": \"minecraft:block/chiseled_polished_blackstone\",
 \"gold\": \"minecraft:block/gold_block\", \"frame\": \"minecraft:block/smooth_stone\", \"window\": \"minecraft:block/black_concrete\",
 \"soul\": \"$M:block/pure_soul_still\", \"amethyst\": \"minecraft:block/amethyst_block\", $REFINER_INLAY_TEXTURES"
# name, body elements, an item's extra elements; then the window's fill per level comes from fill_<name> (prints the
# element for a level), and a flow model is used while lit if one was written
machine() {
    w $A/models/block/$1.json <<EOF
{"parent": "minecraft:block/block", "textures": {$MACHINE_TEXTURES}, "render_type": "minecraft:cutout", "elements": [$2]}
EOF
    w $A/models/item/$1.json <<EOF
{"parent": "minecraft:block/block", "textures": {$MACHINE_TEXTURES}, "render_type": "minecraft:cutout", "elements": [$2, $3]}
EOF
    for level in 1 2 3 4 5 6 7 8; do
        w $A/models/block/${1}_level_$level.json <<EOF
{"textures": {"particle": "$M:block/pure_soul_still", "soul": "$M:block/pure_soul_still"}, "render_type": "minecraft:translucent",
 "elements": [$(fill_$1 $level)]}
EOF
    done
    {
        printf '{"multipart": ['
        sep=""
        n=$1
        for f in "north 0" "east 90" "south 180" "west 270"; do
            set -- $f "$n"
            part() { printf '%s\n  {"when": {%s}, "apply": {"model": "%s", "y": %s}}' "$sep" "$1" "$2" "$3"; sep=","; }
            part "\"facing\": \"$1\"" $M:block/$3 $2
            [ -f $A/models/block/${3}_flow.json ] && part "\"facing\": \"$1\", \"lit\": \"true\"" $M:block/${3}_flow $2
            [ -f $A/models/block/${3}_amethyst.json ] && part "\"facing\": \"$1\", \"amethyst\": \"true\"" $M:block/${3}_amethyst $2
            for level in 1 2 3 4 5 6 7 8; do part "\"facing\": \"$1\", \"level\": \"$level\"" $M:block/${3}_level_$level $2; done
        done
        printf '\n]}\n'
    } > $A/blockstates/$1.json
}

# The Fuser: two jars on their pads (left and right), their necks in gold sockets under a beam that carries both souls
# to the middle, where the fused soul fills a third jar on a pedestal. Its bar shows in a long window in the slab.
FUSER_BODY="$(el 0 0 0 16 5 16 polished), $(el 0 5 12 16 16 16 bricks), $(el 0 12 6 16 15 12 chiseled),
 $(el 10 5 5.5 15 5.5 10.5 polished), $(el 1 5 5.5 6 5.5 10.5 polished),
 $(el 10 5.5 7.5 10.5 7.5 8.5 gold), $(el 14.5 5.5 7.5 15 7.5 8.5 gold), $(el 1 5.5 7.5 1.5 7.5 8.5 gold), $(el 5.5 5.5 7.5 6 7.5 8.5 gold),
 $(el 11 10 6.5 14 12 9.5 gold), $(el 2 10 6.5 5 12 9.5 gold),
 $(el 6.5 5 6.5 9.5 6.75 9.5 polished), $(el 6.4 6.5 6.4 9.6 7 9.6 gold), $(el 7 11 7 9 12 9 gold),
 $(el 1.5 1 -0.1 14.5 4 0 frame north), $(el 2 1.5 -0.15 14 3.5 -0.1 window north),
 {\"from\": [-0.01, -0.01, -0.01], \"to\": [16.01, 5.01, 16.01], \"faces\": {$(uv band south 0 11 16 16 east 0 11 16 16 west 0 11 16 16)}}"
fill_soul_fuser() { printf '%s' "$(el $(( 14 - 12 * $1 / 8 )) 1.5 -0.2 14 3.5 -0.15 soul north)"; }
# souls flowing along the beam's front from both ends to the middle
w $A/models/block/soul_fuser_flow.json <<EOF
{"textures": {"particle": "$M:block/soul_flow", "flow": "$M:block/soul_flow"}, "render_type": "minecraft:translucent",
 "elements": [{"from": [8, 12, 5.98], "to": [15, 15, 6], "shade": false, "faces": {"north": {"uv": [1, 1, 8, 4], "texture": "#flow"}}},
  {"from": [1, 12, 5.98], "to": [8, 15, 6], "shade": false, "faces": {"north": {"uv": [15, 1, 8, 4], "texture": "#flow"}}}]}
EOF
machine soul_fuser "$FUSER_BODY" "$(fill_soul_fuser 4)"

# The Distiller: a column in front of the wall. The jar hangs upside down from a gold ring at the top, its neck in a gold
# funnel; under it a column of amethyst between gold rings (while it is in its slot; souls run down it while it works),
# and the test tube on its pad at the bottom (drops fall from the funnel while it works). Its bar shows in a tower on the
# right, like the Refiner's.
DISTILLER_BODY="$(el 0 0 0 16 1.5 16 polished), $(el 0 1.5 12 16 16 16 bricks), $(el 0 1.5 4 4 16 12 polished),
 $(el 0.5 2 3.9 3.5 15.5 4 frame north), $(el 1 2.5 3.85 3 15 3.9 window north),
 $(el 8 14.2 7 12 14.6 9 gold), $(el 9.5 14.2 9 10.5 14.6 12 gold),
 $(el 9 10.5 7 11 11.5 9 gold), $(el 9.5 10.7 9 10.5 11.3 12 polished),
 $(el 8.8 6.5 6.8 11.2 7 9.2 gold), $(el 9.6 6.6 9.2 10.4 6.9 12 polished),
 $(el 8.5 1.5 6.5 11.5 2 9.5 polished), $(el 8.5 2 7.5 9 3.5 8.5 gold), $(el 11 2 7.5 11.5 3.5 8.5 gold),
 {\"from\": [-0.01, 1.49, 3.99], \"to\": [4.01, 16.01, 12.01], \"faces\": {$(uv tower east 4 0 12 14.5 west 4 0 12 14.5)}},
 {\"from\": [-0.01, 1.49, 11.99], \"to\": [16.01, 16.01, 16.01], \"faces\": {$(uv wall north 0 0 16 14.5)}}"
fill_soul_distiller() { printf '%s' "$(el 1 2.5 3.8 3 $(awk "BEGIN{print 2.5 + 12.5 * $1 / 8}") 3.85 soul north)"; }
w $A/models/block/soul_distiller_amethyst.json <<EOF
{"textures": {"particle": "minecraft:block/amethyst_block", "amethyst": "minecraft:block/amethyst_block"},
 "elements": [$(el 9 7 7 11 10.5 9 amethyst)]}
EOF
# souls running down the amethyst: the flow texture turned on its side
w $A/models/block/soul_distiller_flow.json <<EOF
{"textures": {"particle": "$M:block/soul_flow", "flow": "$M:block/soul_flow"}, "render_type": "minecraft:translucent",
 "elements": [{"from": [8.98, 7, 6.98], "to": [11.02, 10.5, 9.02], "shade": false, "faces": {
   "north": {"uv": [4, 5, 7.5, 7], "rotation": 90, "texture": "#flow"}, "south": {"uv": [4, 5, 7.5, 7], "rotation": 90, "texture": "#flow"},
   "east": {"uv": [4, 5, 7.5, 7], "rotation": 90, "texture": "#flow"}, "west": {"uv": [4, 5, 7.5, 7], "rotation": 90, "texture": "#flow"}}}]}
EOF
machine soul_distiller "$DISTILLER_BODY" "$(fill_soul_distiller 4), $(el 9 7 7 11 10.5 9 amethyst)"

w $D/loot_tables/blocks/soul_refiner.json <<EOF
{"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
  "entries": [{"type": "minecraft:item", "name": "$M:soul_refiner"}],
  "conditions": [{"condition": "minecraft:survives_explosion"}]}]}
EOF
w $D/recipes/soul_refiner.json <<EOF
{"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["QJQ", "GHG", "BBB"],
 "key": {"Q": {"item": "minecraft:quartz"}, "J": {"item": "$M:soul_jar"}, "G": {"item": "minecraft:glass"},
         "H": {"item": "minecraft:hopper"}, "B": {"item": "minecraft:polished_blackstone"}},
 "result": {"item": "$M:soul_refiner"}}
EOF
# A bottle of pure soul soaks four coal (or charcoal); the bottle is given back.
w $D/recipes/soul_coal.json <<EOF
{"type": "minecraft:crafting_shapeless", "category": "misc",
 "ingredients": [{"item": "$M:pure_soul_bottle"}, {"tag": "minecraft:coals"}, {"tag": "minecraft:coals"}, {"tag": "minecraft:coals"}, {"tag": "minecraft:coals"}],
 "result": {"item": "$M:soul_coal", "count": 4}}
EOF

# ---------- creatures no jar or collar can hold
w $D/tags/entity_types/uncapturable.json <<EOF
{"replace": false, "values": [
  {"id": "#forge:bosses", "required": false},
  "minecraft:ender_dragon", "minecraft:wither", "minecraft:warden", "minecraft:elder_guardian"
]}
EOF
echo "wrote DV Science of Souls JSON"
