#!/bin/bash
# Run from anywhere (bash, e.g. Git Bash):  bash tools/make-backpack-models.sh
# Writes DV Backpack's three block models from the same parts:
#   backpack.json       set down, the sleeping bag rolled up in front of it
#   backpack_bare.json  set down, the sleeping bag laid out (so not on the backpack)
#   backpack_worn.json  on the back: the same bag, raised, with the sleeping bag rolled up underneath
out="$(dirname "$0")/../dvbackpack/src/main/resources/assets/dvbackpack/models/block"
first=1
add() { # name x0 y0 z0 x1 y1 z1 texture tint [end-texture]
  local n=$1 x0=$2 y0=$3 z0=$4 x1=$5 y1=$6 z1=$7 t=$8 tint=$9 e=${10:-$8}
  local ti=""; [ "$tint" != "-" ] && ti=", \"tintindex\": $tint"
  [ $first = 1 ] || printf ',\n'; first=0
  printf '    {"name": "%s", "from": [%s, %s, %s], "to": [%s, %s, %s], "faces": {\n' $n $x0 $y0 $z0 $x1 $y1 $z1
  printf '      "north": {"texture": "#%s"%s}, "south": {"texture": "#%s"%s},\n' $t "$ti" $t "$ti"
  printf '      "east": {"texture": "#%s"%s}, "west": {"texture": "#%s"%s},\n' $e "$ti" $e "$ti"
  printf '      "up": {"texture": "#%s"%s}, "down": {"texture": "#%s"%s}}}' $t "$ti" $t "$ti"
}
y() { awk -v a="$1" -v b="$dy" "BEGIN { print a + b }"; }
sum() { awk -v a="$1" -v b="$2" "BEGIN { print a + b }"; }
bag() {
  add bag 3.5 $(y 0) 6 12.5 $(y 12) 11 leather -
  add lid 3.3 $(y 8.5) 5.8 12.7 $(y 12.3) 11.2 dark -
  add lid_buckle 7 $(y 7.4) 5.5 9 $(y 9) 5.8 buckle 0
  add strap_front_left 5 $(y 4.8) 5.6 6 $(y 12.5) 6 strap 0
  add strap_front_right 10 $(y 4.8) 5.6 11 $(y 12.5) 6 strap 0
  add strap_top_left 5 $(y 12.3) 5.6 6 $(y 12.5) 9 strap 0
  add strap_top_right 10 $(y 12.3) 5.6 11 $(y 12.5) 9 strap 0
  add pocket 4.3 $(y 0.5) 4.3 11.7 $(y 4.5) 6 leather -
  add pocket_flap 4.1 $(y 3.3) 4.1 11.9 $(y 4.8) 6 dark -
  add pocket_buckle 7.3 $(y 2.3) 3.9 8.7 $(y 3.5) 4.1 buckle 0
  add shoulder_left 4.5 $(y 1) 11 6 $(y 11) 11.6 trim -
  add shoulder_right 10 $(y 1) 11 11.5 $(y 11) 11.6 trim -
}
roll() { # z0 z1: where the roll lies
  add roll 3 0 $1 13 3 $2 roll 1 roll_end
  add roll_strap_left 4.8 -0.2 $(sum $1 -0.2) 5.8 3.2 $(sum $2 0.2) strap 0
  add roll_strap_right 10.2 -0.2 $(sum $1 -0.2) 11.2 3.2 $(sum $2 0.2) strap 0
}
model() { # file dy roll-z0 roll-z1 (no roll if empty)
  first=1; dy=$2
  {
    printf '{\n  "parent": "block/block",\n  "textures": {\n'
    printf '    "leather": "dvbackpack:block/backpack_leather",\n    "dark": "dvbackpack:block/backpack_leather_dark",\n'
    printf '    "strap": "dvbackpack:block/backpack_strap",\n    "buckle": "dvbackpack:block/backpack_buckle",\n'
    printf '    "trim": "dvbackpack:block/backpack_trim",\n    "roll": "dvbackpack:block/sleeping_bag_side",\n'
    printf '    "roll_end": "dvbackpack:block/sleeping_bag_top",\n    "particle": "dvbackpack:block/backpack_leather"\n  },\n'
    printf '  "elements": [\n'
    bag
    [ -n "$3" ] && roll $3 $4
    printf '\n  ]\n}\n'
  } > "$out/$1"
}
model backpack.json 0 0.4 4
model backpack_bare.json 0
model backpack_worn.json 3 6.3 10.7
