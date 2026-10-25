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
| DV Harvest | `dvharvest-1.20.1-0.1.0.jar` | ikisi (zorunlu) | DV Vein isteğe bağlı (toplu hasat) | Utility, Farming |

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
**Summary:** Hold a key to break a whole vein at once.

- Hold **`** (grave) and break a block: connected blocks of the same kind break too, diagonals included
- Ore variants count as one vein (iron + deepslate iron, and modded ores sharing a `forge:ores/*` tag)
- Outline preview and block counter while the key is held
- Drops and XP gather where you started
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

## DV Harvest
**Summary:** Right-click a ripe crop to harvest and replant it.

- Right-click a fully grown crop: it drops its harvest and goes back to a seedling
- One seed from the drops is used for replanting
- Wheat, carrots, potatoes, beetroot, nether wart, cocoa, and modded crops built on vanilla's crop block (e.g. Farmer's Delight)
- Other modded crops: add them to the `dvharvest:harvestable` block tag; exclude blocks with `dvharvest:blacklist`
- With **DV Vein** installed: hold the vein key and right-click to harvest the whole connected field at once
- Fortune on your held tool applies; sneak to use items normally
- Respects claim/protection permissions
- Required on both client and server

---

## Değişiklik listesi

### 0.1.0 (ilk sürüm)
İlk yayın: DV Lib, DV Zoom, DV Food, DV Lens, DV Vein, DV Slots, DV Dark.

## Yayın öncesi kontrol listesi
- [x] Forge 1.20.1 ayrılmış sunucuda açılıyor (2026-10-25)
- [x] NeoForge 1.20.1 (47.1.106) ayrılmış sunucuda gerçek jar'larla açılıyor, mixin'ler uygulanıyor (2026-10-25)
- [ ] NeoForge istemcisinde (başlatıcıyla) bir kez dene
- [ ] Mod ikonları (`logoFile` mods.toml'da)
- [ ] GitHub deposu açılınca `displayURL` ve `issueTrackerURL` ekle
- [ ] Ekran görüntüleri (her sayfa için 2–3)
