# DV modları — yayın metinleri (Modrinth / CurseForge)

Her mod ayrı bir proje sayfası olarak yüklenir; DV Lib diğerlerinin zorunlu bağımlılığıdır (DV Food sunucuda DV Lib istemez).
Ortak bilgiler: **Minecraft 1.20.1 · Forge 47+ ve NeoForge 47.1+ · Lisans MIT · Yazar Darkvolfi**

| Mod | Jar | Taraf | Bağımlılık | Kategori önerisi |
|---|---|---|---|---|
| DV Lib | `dvlib-1.20.1-0.1.0.jar` | ikisi | — | Library |
| DV Zoom | `dvzoom-1.20.1-0.1.0.jar` | istemci | DV Lib | Utility |
| DV Food | `dvfood-1.20.1-0.1.0.jar` | ikisi (ikisi de isteğe bağlı) | DV Lib (istemci) | Food, Utility |
| DV Lens | `dvlens-1.20.1-0.1.0.jar` | istemci (sunucuda isteğe bağlı) | DV Lib (istemci) | Utility |
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
- Search box: find any DV setting by name
- **Profiles:** save all DV settings and key bindings under a name, then load them in another world or instance
- Shows when a DV mod has an update (Forge's version check)
- English and Turkish

## DV Zoom
**Summary:** Hold a key to zoom in, scroll to adjust.

- Hold **C** to zoom (4× by default)
- Mouse wheel changes the zoom from 1× to 50× while zooming, without switching hotbar slots
- Smooth transition and cinematic camera, both optional
- Mouse gets slower the further you zoom in
- Hides the HUD and your hand while zooming
- Every option can be turned off
- Client-side only

## DV Food
**Summary:** See saturation, exhaustion, and what food will restore — including health.

- Gold outline on the hunger icons covered by saturation
- While holding food: the hunger, saturation **and health** it would restore flash on the bars
- Exhaustion bar behind the hunger bar
- Food tooltips show hunger and saturation as icons
- Food tooltips list the effects a food gives, like a potion: golden apples, pufferfish, rotten flesh... with duration and chance
- Hunger, saturation and exhaustion in the F3 screen
- Install on the server too for exact saturation/exhaustion sync (optional on both sides)

## DV Lens
**Summary:** Shows what you're looking at.

- Name, icon and mod of the block or entity you're looking at; mobs show their face
- For blocks that need a tool: the minimum tool's icon with ✔/✘ for your held item
- Crop growth, entity health, dropped item counts
- Animals: baby or adult, time until grown up and until they can breed again
- Villager profession and level, owner of tamed animals
- Redstone: power level, repeater delay and lock, comparator mode, what an observer watches
- Which mob a spawner spawns
- Container contents as icons (chests, barrels, modded inventories), furnace and brewing progress and fuel
- Potion effects on mobs
- Machines from any mod: stored energy (FE/RF) and fluid tanks; Create rotation speed and network stress
- Note block note and instrument, unstable TNT, honey level and bees in a hive, command block command (operators only)
- Painting title and artist, item frame item, armor stand gear
- Progress bar while breaking a block
- Server setting to stop sharing container contents
- Horse, donkey, mule and camel speed (blocks/s) and jump height (blocks)
- Toggle key; every line can be turned off
- Works client-side only; install on the server too for container contents, furnace progress, mob effects, bees, exact animal growth times and offline pet owner names

## DV Vein
**Summary:** Hold a key to break a whole vein, harvest a whole field or shear every sheep around — straight into your inventory.

- Hold **`** (grave) and break a block: connected blocks of the same kind break too, diagonals included
- Ore variants count as one vein (iron + deepslate iron, and modded ores sharing a `forge:ores/*` tag)
- **Shapes** — scroll the mouse wheel while holding the key: Vein, Small tunnel (1×1), Mining tunnel (stairs down), Large tunnel (3×3), 3×3 square, Escape tunnel (stairs up)
- Outline preview and block counter while the key is held
- Drops and XP go straight into your inventory (what doesn't fit drops at your feet)
- **Harvest:** right-click a fully grown crop to harvest it and replant it from its own seed. Wheat, carrots, potatoes, beetroot, nether wart, cocoa, and modded crops built on vanilla's crop block (e.g. Farmer's Delight); other crops via the `dvvein:harvestable` block tag, exclude with `dvvein:blacklist`. Fortune applies, sneak to use items normally, respects claims
- **Whole field:** hold the key and right-click a crop to harvest every connected ripe crop of that kind into your inventory
- **Plant a field:** hold the key and right-click farmland or grass with seeds, potatoes, carrots or saplings to plant every connected free spot (uses more of the same seeds from your inventory)
- **Till and make paths:** hold the key and right-click with a hoe to till, or a shovel to make paths, across the whole connected patch
- **Blacklist in-game:** hold the key and middle-click a block so the vein key never breaks it (middle-click again to undo)
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
- Choose how dark, and an accent colour (blue, purple, green, red, orange)
- Dark item tooltips with an accent-coloured border
- Exception list: leave chosen mods' screens as they are
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
- **Share waypoints** in chat: players with DV Map add them with one click
- **Structures** you've been inside (villages, fortresses, temples...) are marked on the map (needs DV Map on the server)
- **Save the map as PNG** into your screenshots folder
- Nether ↔ Overworld coordinates (÷8 / ×8) under the minimap
- Client-side (install on the server too for player positions and structure markers); integrates with **DV Team** when installed

## DV Team
**Summary:** Clans with a flag, a capital, villages and trade centers on DV Map — with alliances and wars.

- **Clans (Y):** found a clan with a name, a short tag, any colour you like and a banner-style flag (up to 6 pattern layers); ranks: leader, officers, members
- **No clan, no land:** only clans can claim chunks
- **Settlements:** your first claim founds the **capital**; land elsewhere becomes a named **village** or **trade center**. Names and icons show on the map in your clan colour, the capital with a castle and your flag
- **Permissions per settlement:** choose what allies and everyone else may do — build, doors, containers, animals, farming, villager trading
- **Trade centers:** no PvP and no raids; outsiders get more freedom by default
- **Wars:** declare war, a warm-up, then PvP and raids on enemy land (trade centers and the capital core stay safe; no raids while the defenders are all offline). Ends by peace, surrender or time
- **Alliances:** offer and accept; friendly fire follows each clan's setting
- **Clan level:** members and land earn points; higher levels unlock more chunks and force-loaded chunks
- **Clan vault:** a shared chest that opens on your clan's land; the leader picks which ranks may use it
- **Clan and ally chat:** `/clan msg`, `/clan ally`, or switch your normal chat with `/clan chat clan|ally|all`
- Entering a settlement shows its name as a title
- **Server limits:** chunks per clan, villages, trade centers, village size, force-loaded chunks, gap between clans, war timings
- Claim from the world map (**M** → Claims), or `/clan claim`; force-load chunks with Shift
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
- [ ] Modrinth projeleri açılınca her modun `mods.toml`'una `updateJSONURL` ekle (DV menüsündeki güncelleme işareti bunu kullanır)
- [ ] Ekran görüntüleri (her sayfa için 2–3)
