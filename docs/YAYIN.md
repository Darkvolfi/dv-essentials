# DV modları — yayın metinleri (Modrinth / CurseForge)

Her mod ayrı bir proje sayfası olarak yüklenir; DV Lib diğerlerinin zorunlu bağımlılığıdır (DV Food sunucuda DV Lib istemez).
Ortak bilgiler: **Minecraft 1.20.1 · Forge 47+ ve NeoForge 47.1+ · Lisans MIT · Yazar Darkvolfi**

| Mod | Jar | Taraf | Bağımlılık | Kategori önerisi |
|---|---|---|---|---|
| DV Lib | `dvlib-1.20.1-0.1.0.jar` | ikisi | — | Library |
| DV Zoom | `dvzoom-1.20.1-0.1.0.jar` | istemci | DV Lib | Utility |
| DV Food | `dvfood-1.20.1-0.1.0.jar` | ikisi (ikisi de isteğe bağlı) | DV Lib (istemci) | Food, Utility |
| DV Lens | `dvlens-1.20.1-0.1.0.jar` | istemci | DV Lib | Utility |
| DV Vein | `dvvein-1.20.1-0.1.0.jar` | ikisi (zorunlu) | DV Lib | Utility |
| DV Slots | `dvslots-1.20.1-0.1.0.jar` | ikisi (zorunlu) | DV Lib | Equipment |
| DV Dark | `dvdark-1.20.1-0.1.0.jar` | istemci | DV Lib | Decoration, Utility |
| DV Map | `dvmap-1.20.1-0.1.0.jar` | istemci | DV Lib | Map, Utility |
| DV Team | `dvteam-1.20.1-0.1.0.jar` | ikisi (zorunlu) | DV Lib, DV Map (istemci) | Utility, Multiplayer |

Jar'lar: `<mod>/build/libs/` (`.\gradlew.bat build` ile üretilir).

---

## Ortak kısa paragraf (her sayfanın sonuna)

> Part of **Darkvolfi's DV mods** — small, focused quality-of-life mods that share one library (DV Lib). Every DV mod adds its settings and key bindings to the **DV** button on the title screen and pause menu. Written from scratch; no code from other mods.

---

## DV Lib
**Summary:** Shared library for Darkvolfi's DV mods.

The library behind the DV mods. Install it together with any DV mod.
- **DV** button on the title screen and in the pause menu, listing your installed DV mods
- One settings screen for every DV mod: toggles, number fields with validation, tooltips, and key bindings you can change right there
- English and Turkish

## DV Zoom
**Summary:** Hold a key to zoom in, scroll to adjust.

- Hold **C** to zoom (4× by default)
- Mouse wheel changes the zoom from 1× to 50× while zooming, without switching hotbar slots
- Smooth transition and cinematic camera, both optional
- Only the world zooms; your hand stays the same
- Client-side only

## DV Food
**Summary:** See saturation, exhaustion, and what food will restore — including health.

- Gold outline on the hunger icons covered by saturation
- While holding food: the hunger, saturation **and health** it would restore flash on the bars
- Exhaustion bar behind the hunger bar
- Food tooltips show hunger and saturation as icons
- Hunger, saturation and exhaustion in the F3 screen
- Install on the server too for exact saturation/exhaustion sync (optional on both sides)

## DV Lens
**Summary:** Shows what you're looking at.

- Name, icon and mod of the block or entity you're looking at
- For blocks that need a tool: the minimum tool (e.g. "Needs: Iron Pickaxe") with ✔/✘ for your held item
- Crop growth, entity health, dropped item counts
- Horse, donkey, mule and camel speed (blocks/s) and jump height (blocks)
- Toggle key; every line can be turned off
- Client-side only

## DV Vein
**Summary:** Hold a key to break a whole vein, harvest a whole field or shear every sheep around — straight into your inventory.

- Hold **`** (grave) and break a block: connected blocks of the same kind break too, diagonals included
- Ore variants count as one vein (iron + deepslate iron, and modded ores sharing a `forge:ores/*` tag)
- **Shapes** — scroll the mouse wheel while holding the key: Vein, Small tunnel (1×1), Mining tunnel (stairs down), Large tunnel (3×3), 3×3 square, Escape tunnel (stairs up)
- Outline preview and block counter while the key is held
- Drops and XP go straight into your inventory (what doesn't fit drops at your feet)
- **Harvest:** right-click a fully grown crop to harvest it and replant it from its own seed. Wheat, carrots, potatoes, beetroot, nether wart, cocoa, and modded crops built on vanilla's crop block (e.g. Farmer's Delight); other crops via the `dvvein:harvestable` block tag, exclude with `dvvein:blacklist`. Fortune applies, sneak to use items normally, respects claims
- **Whole field:** hold the key and right-click a crop to harvest every connected ripe crop of that kind into your inventory
- **Shear them all:** hold the key and shear a sheep to shear every sheep within 32 blocks (3 up, 3 down); the wool goes into your inventory
- Safe: stops before your tool breaks or when you run out of hunger; in survival only works with a tool that actually drops the block
- Server settings: max blocks (64), hunger per block, ore grouping
- Required on both client and server

## DV Slots
**Summary:** Wear an elytra and a chestplate at the same time.

- Adds a **Back** slot for your elytra
- Right-click an elytra to put it straight into the back slot
- **DV** button in the inventory (or a key) opens the DV slots screen
- Flies exactly like a normal elytra and wears down the same way
- Visible on your back to everyone, uses your cape texture if you have one
- **Saddle** slot: mounting a tamed horse, donkey, mule or camel puts your saddle on it; dismounting takes it back
- Right-click a saddle in the air to put it into the saddle slot
- Dropped on death unless keepInventory is on
- Required on both client and server

## DV Dark
**Summary:** Dark mode for menus and inventories — including other mods' screens.

- Darkens GUI textures as they load: grey becomes dark, colours stay, bevels still read correctly
- Works on modded screens too, no resource pack needed
- Dark label text is brightened so it stays readable
- Toggle with a key or from the DV menu
- Client-side only

## DV Map
**Summary:** Customizable minimap, full-screen world map, waypoints and entity radar.

- **Minimap:** choose corner, square or circle shape, size, zoom, north-up or rotate-with-player, plus optional coordinates, biome name, clock time and chunk grid
- **Full-screen World Map (M):** drag to pan, scroll to zoom, inspect coordinates and biomes, right-click anywhere to add a waypoint
- **Surface & Cave modes:** biome-tinted terrain with slope/water depth shading and torch/lava glow, plus an underground cave slice view (automatic when under a roof)
- **Persistent exploration:** discovered surface regions are saved per world and dimension
- **Waypoints (B):** custom colours, in-world light beams and distance labels, quick teleport (when permitted), and automatic Last Death waypoint
- **Entity Radar:** toggleable markers for players, hostiles, animals and dropped items
- Client-side only; integrates with **DV Team** when installed

## DV Team
**Summary:** Teams, chunk claiming, chunk force-loading and territory protection on DV Map.

- **Claim on DV Map:** open the world map (**M**), enable **Claims**, and click or drag across chunks to claim, unclaim, or force-load them
- **Connected territory borders:** claimed chunks appear on the world map and minimap in your team's colour
- **Force-loading (chunkloader):** keep selected claimed chunks loaded even while you are away or offline
- **Territory protection:** blocks breaking/placing, container/door interactions, buckets, farmland trampling, entity damage and explosions inside claimed chunks
- **Teams & Allies (Y or /dvteam):** customize team name and colour, invite players, ally other teams, toggle friendly fire, ally permissions and public interaction, or chat with `/dvteam msg`
- Required on both client and server; requires **DV Map** on the client

---

## Değişiklik listesi

### 0.1.0 (ilk sürüm)
İlk yayın: DV Lib, DV Zoom, DV Food, DV Lens, DV Vein, DV Slots, DV Dark.

## Yayın öncesi kontrol listesi
- [x] Forge 1.20.1 ayrılmış sunucuda açılıyor (2026-10-25)
- [x] NeoForge 1.20.1 (47.1.106) ayrılmış sunucuda gerçek jar'larla açılıyor, mixin'ler uygulanıyor (2026-10-25)
- [ ] NeoForge istemcisinde (başlatıcıyla) bir kez dene
- [x] Mod ikonları: `art/icons/<mod>.png` (512, sayfalar için), jar içinde `<mod>_logo.png` (128); yeniden üretmek için `tools/make-icons.ps1`
- [ ] GitHub deposu açılınca `displayURL` ve `issueTrackerURL` ekle
- [ ] Ekran görüntüleri (her sayfa için 2–3)
