#!/bin/bash
# Writes updates/<mod_id>.json, the Forge update file each mod's mods.toml points to (updateJSONURL).
# Run it after a version is published, never before: players are told to update as soon as it is pushed.
#   tools/update-json.sh dvmap 1.0.1.0 [dvteam 1.0.1.0 ...]
# The changelog is the English line under "### <mod name> <version>" in docs/YAYIN.md (empty for first releases).
cd "$(dirname "$0")/.." || exit 1
mkdir -p updates
while [ $# -ge 2 ]; do
    id=$1; ver=$2; shift 2
    name=$(grep '^mod_name=' "$id/gradle.properties" | cut -d= -f2 | tr -d '\r')
    [ -n "$name" ] || { echo "unknown mod $id"; exit 1; }
    log=$(awk -v h="### $name $ver" '$0 == h {p = 1; next} /^### / {p = 0} p && /^Sayfalara yazılacak İngilizce metin:/ {print; exit}' docs/YAYIN.md \
        | tr -d '\r' | sed 's/^[^"]*"//; s/"$//; s/\\/\\\\/g; s/"/\\"/g')
    page=$(grep -o "\"$id\", CURSEFORGE + \"[0-9]*\"" dvlib/src/main/java/com/darkvolfi/dvlib/client/DVMods.java | grep -o '[0-9]*"$' | tr -d '"')
    homepage="https://github.com/Darkvolfi/dv-essentials"
    [ -n "$page" ] && homepage="https://www.curseforge.com/projects/$page"
    [ "$id" = dvlib ] && homepage="https://www.curseforge.com/projects/1786909"
    cat > "updates/$id.json" <<EOF
{
  "homepage": "$homepage",
  "promos": {
    "1.20.1-latest": "$ver",
    "1.20.1-recommended": "$ver"
  },
  "1.20.1": {
    "$ver": "$log"
  }
}
EOF
    echo "updates/$id.json -> $ver"
done
