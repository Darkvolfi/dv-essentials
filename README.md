# DV Essentials

Darkvolfi's DV mods for Minecraft 1.20.1: small, focused mods that share one library and one settings menu. Written from scratch, with no required mods from outside the DV family.

**Status:** the first release (1.0.0.0) is being prepared.

## Mods

| Mod | What it does | Side |
|---|---|---|
| DV Lib | Shared library, the DV menu, settings search and profiles | Both |
| DV Zoom | Hold a key to zoom, scroll to adjust | Client |
| DV Food | Saturation, exhaustion and what food restores | Client, optional on server |
| DV Lens | Shows what you're looking at | Client, optional on server |
| DV Vein | Break veins, harvest fields and shear sheep with one key | Both |
| DV Slots | Back and saddle slots, so you can wear an elytra with a chestplate | Both |
| DV Dark | Dark mode for menus, including other mods' screens | Client |
| DV Map | Minimap, world map, waypoints and entity radar | Client, optional on server |
| DV Team | Clans with settlements, alliances and wars | Both |
| DV Grave | A gravestone keeps your items when you die | Both |
| DV Music | See, pick and add music | Client |
| DV Backpack | Tiered backpacks with upgrade cards and a sleeping bag | Both |
| DV New Crafts | Recipes for hard-to-find items, and copper gear | Both |
| DV Science of Souls | Catch souls, fuse hybrid creatures, bind pets | Both |

Every mod except DV New Crafts needs DV Lib.

## Building

You need JDK 17.

```
gradlew build
```

The jars are written to `<mod>/build/libs/`.

## Versions

Versions are `x.y.z.a`: `x` is DV Lib's first number (DV New Crafts, which doesn't use DV Lib, has its own), `y` is an update, `z` is a small update or debugging change, and `a` is a bug fix.

## License

MIT, see [LICENSE](LICENSE). The copper textures of DV New Crafts are derived from Minecraft's textures and are not covered by it.
