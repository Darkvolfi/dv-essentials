# DV Essentials — Yol haritası

Darkvolfi'nin alışık olduğu oyun içi kolaylıkları kendi "DV" ekosisteminde toplayan Minecraft modları.

- **Minecraft:** 1.20.1 · **Yükleyici:** Forge 47.4.x; aynı jar NeoForge 1.20.1'de de çalışıyor (47.1.106 sunucusunda doğrulandı, 2026-10-25) · **Lisans:** MIT
- **İlke:** Benzer modların (FTB Ultimine, Jade, AppleSkin, Curios…) kodu kopyalanmaz; özellikler sıfırdan yazılır. DV dışı mod bağımlılığı yok.

## Yapı (karar, 2026-10-25)

Tek depo, çok modül. Her özellik **ayrı bir mod** (ayrı jar), hepsi ortak **DV Lib**'e bağlı — FTB modları ile FTB Library gibi. Oyuncu sadece istediğini kurar.

```
DV/
├── gradle/dv-mod.gradle   ← tüm modların ortak derleme ayarı
├── gradle.properties      ← ortak: MC/Forge sürümü, yazar, lisans
├── dvlib/                 ← DV Lib: ortak kod (şimdilik "DV Modları" tuş kategorisi)
├── dvzoom/                ← DV Zoom
├── …                      ← her yeni mod: kendi klasörü + gradle.properties (mod_id, ad, sürüm)
└── dvdev/                 ← mod değil: tüm DV modlarını birlikte açan geliştirme çalıştırması
```

- Ortak bir şey **en az iki modda** gerekince DV Lib'e taşınır (ayar ekranı, ağ paketleri, koyu tema renkleri…). Önceden tahminle doldurulmaz.
- Jar adı: `<mod_id>-1.20.1-<sürüm>.jar`. Her modun sürümü ayrı.

## Modlar

| # | Mod | Ne yapar | Benzediği | Taraf | Durum |
|---|---|---|---|---|---|
| — | **DV Lib** (`dvlib`) | Ortak kod | FTB Library | ikisi | ✅ 0.1.0 |
| M1 | **DV Zoom** (`dvzoom`) | C'ye basılı tutunca yakınlaştırma (varsayılan 4×), tekerlekle 1×–50×, yumuşak geçiş, sinematik kamera | OptiFine zoom | istemci | ✅ 0.1.0, oyunda denendi |
| M2 | **DV Food** (`dvfood`) | Doygunluk kadar butun çevresinde altın kenar; elde yemek varken kazanılacak açlık/doygunluk ve doğal yenilenmeyle dolacak can yanıp söner; çubuğun arkasında yorgunluk (exhaustion) şeridi; tooltip'te açlık ve altın kenarlı doygunluk ikonları; F3'te açlık/doygunluk/yorgunluk satırı. Sunucuda da kuruluysa doygunluk/yorgunluk her değiştiğinde senkronlanır. Altın kenar, vanilya `icons.png`'den çalışma anında çıkarılır (doku dağıtılmaz, doku paketleriyle uyumlu) | AppleSkin | ikisi (iki tarafta da isteğe bağlı) | ✅ 0.1.0, oyunda denendi |
| M3 | **DV Lens** (`dvlens`) | Ekranın üst ortasında tooltip tarzı kutu: bakılan blok/varlığın ikonu, adı, modu; gereken alet ve seviye (✔/✘ elindekiyle kırılır mı); ekin büyümesi; canlı canı; at/eşek/katır/deve hızı (blok/sn, hız × 42.16) ve zıplama yüksekliği (blok, wiki eğrisi; devede yok, lamada hiç yok); yerdeki eşya ve sayısı. Aç/kapa tuşu (varsayılan atanmamış) | Jade | istemci | ✅ 0.1.0, oyunda denendi (alet satırı sadece doğru alet gerektiren bloklarda: "En az: Taş Kazma") |
| M3.1 | DV Lens sunucu bilgileri | Sandık/fırın içeriği, fırın ilerlemesi, spawner vb. (sunucu paketi gerekir) | Jade | ikisi | ⏳ |
| M4 | **DV Vein** (`dvvein`) | ` tuşu basılıyken kırılan bloğa (çaprazlar dahil) bağlı aynı bloklar da kırılır; `forge:ores/*` etiketini paylaşan cevherler birlikte (demir + derin demir). Altın çizgili önizleme ve "Damar: N blok" sayacı. Sunucu ayarı: en çok 64 blok, blok başına ek açlık; alet kırılmak üzereyken ya da açlık bitince durur; Hayatta Kalma'da doğru alet yoksa çalışmaz (Yaratıcı'da her blokta çalışır). Düşenler ve XP doğrudan envantere gelir (sığmayan ayağının dibine düşer). Kırma `ServerPlayerGameMode.destroyBlock` ile (koruma modları, aşınma, istatistik vanilya gibi) | FTB Ultimine | ikisi | ✅ 0.1.0, oyunda denendi |
| M4.1 | DV Vein şekilleri | Vein tuşu basılıyken fare tekerleği şekil seçer (nişangâh altında yazar): Damar, Küçük tünel 1×1, Maden tüneli (aşağı inen merdiven), Büyük tünel 3×3, 3×3 yüzey, Kaçış tüneli (yukarı çıkan, 3 blok yüksek merdiven). Boşluğa gelince durur, bağlı olmayan uzak blokları kırmaz. Şekilli modlar blok türü aramaz; konteyner, sıvı, kırılamaz ve (Hayatta Kalma'da) aletle düşmeyen blokları atlar. Yön bakışa göre (`Direction.orderedByNearest`), kaçış tüneli yatay bakışa göre. `VeinShape` istemci önizlemesi ve sunucuda ortak; ağ sürümü 2 | FTB Ultimine | ikisi | ✅ oyunda denendi (2026-10-27) |
| M4.2 | DV Vein: hasat ve kırpma birleşti | DV Harvest DV Vein'in içine taşındı (kullanıcının kararı): sağ tıkla hasat aynen, vein tuşu + sağ tık tüm tarlayı envantere hasat eder. Vein tuşu basılıyken bir koyunu kırpınca 32 blok yarıçapta (64 çap), 3 üst 3 alt tüm koyunlar kırpılır, yün envantere gelir; makas kırılmak üzereyken ya da açlık bitince durur. Damar kırmada da düşenler ve XP envantere gelir | FTB Ultimine + Right Click Harvest | ikisi | ✅ oyunda denendi (2026-10-27) |
| M5 | **DV Slots** (`dvslots`) | Oyuncuya kayıtlı DV slotları (capability). İlk slot **Sırt**: elytra (ya da `dvslots:back` etiketli eşya) göğüs zırhıyla aynı anda. Envanterde "DV" düğmesi ve tuş (varsayılan atanmamış) DV slot ekranını açar; elinde elytra ile sağ tık doğrudan sırt slotuna takar. Uçuş 3 Mixin ile (`LivingEntity.updateFallFlying`, `Player.tryToStartFallFlying`, `LocalPlayer.aiStep` içindeki `getItemBySlot(CHEST)`), aşınma sırttaki elytra'ya işler. Sırtta görünür (cape/skin elytra dokusu dahil), diğer oyunculara senkronlanır. Ölümde keepInventory kapalıysa düşer | Curios / Elytra Slot | ikisi | ✅ 0.1.0, oyunda denendi |
| M5.1 | DV Slots ek slotlar | Yeni slot türleri (ör. tılsım/yüzük), Mending'in sırttaki elytra'yı onarması, pelerin ile elytra'nın üst üste binmesini önleme | Curios | ikisi | ⏳ |
| M6 | **DV Dark** (`dvdark`) | `textures/gui/` altındaki dokular yüklenirken gri pikseller koyulaştırılır (renkliler korunur, ton sırası korunduğu için kabartmalar bozulmaz); başka modların arayüzleri de dahil. HUD ikonları ve menü logosu hariç. Koyu gri etiket yazıları (0x404040) ekran açıkken açık griye çevrilir. Doku dağıtılmaz. Aç/kapa tuşu dokuları yeniden yükler. 2 Mixin: `SimpleTexture.load` (NativeImage STORE), `GuiGraphics.drawString` (vanilya int sürümleri) | Dark Mode Everywhere | istemci | ✅ 0.1.0, oyunda denendi |
| M7 | **DV Lib ortak parçalar** | Ana ekranda ve Esc menüsünde ("Seçenekler"in solunda) **DV** düğmesi → "DV Modları" menüsü (kurulu her DV modu bir düğme). Ortak ayar ekranı: Mods listesindeki **Config** düğmesi de aynı ekranı açar; açık/kapalı düğmeleri, sayı kutuları (geçersiz değer kırmızı, Bitti kapanır), açıklamalar tooltip'te, TR/EN etiketler. Sunucu ayarları (DV Vein) yalnızca tek oyunculu dünya içinde. Ayar ekranının altında modun tuşları (`key.<modid>.*`): tıkla, yeni tuşa/fare tuşuna bas, Esc boşaltır, çakışan kırmızı; hemen kaydedilir. DV Slots menüde sadece tuşuyla. `DVTheme`: DV Dark açıkken düz renkle çizilen ekranlar (DV Slots) da koyulaşır | — | istemci | ✅ oyunda denendi |
| M8 | İlk yayın | MIT lisansı, Modrinth + CurseForge, yerel git (gizli GitHub sonra). Forge ve NeoForge 1.20.1 (47.1.106) ayrılmış sunucularda test edildi. Sayfa metinleri `docs/YAYIN.md` | — | — | 🔄 yükleme kullanıcıda; ikonlar hazır (tools/make-icons.ps1), ekran görüntüsü eksik |
| M9 | **DV Harvest** → 2026-10-27'de DV Vein'e katıldı (`dvvein`, etiketler `dvvein:harvestable` / `dvvein:blacklist`) | Olgun ekine sağ tık (ana el, eğilmeden): ürün düşer, ekin yaş 0'a döner (diğer özellikleri korunur), düşenlerden bir tohum eksilir. Vanilya + `CropBlock` türevi mod ekinleri, Nether siğili, kakao; diğerleri `dvvein:harvestable` etiketiyle, istenmeyenler `dvvein:blacklist` ile. Fortune işler, bölge koruma (`mayInteract`/`mayBuild`) gözetilir, blok kırma kadar açlık. Sunucuda yoksa istemci tıklamayı yutmaz (boş kanal ile varlık kontrolü). **DV Vein ile:** vein tuşu basılıyken sağ tık, bağlı aynı türdeki tüm olgun ekinleri hasat edip yeniden diker (DV Vein'in sınırı ve açlık ayarıyla; düşenler tıklanan ekinde toplanır). DV Vein isteğe bağlı (compileOnly + ModList kontrolü). Kullanıcının fikri, 2026-10-25 | Right Click Harvest | ikisi | ✅ 0.1.0 oyunda denendi (tek ekin ve Vein ile toplu hasat) |
| M5.2 | **DV Slots eyer slotu** | **Eyer** slotu (`dvslots:saddle` etiketi). Evcil, eyersiz at/eşek/katır/deveye binince slottaki eyer takılır (atın 400 yuvası), inince geri slota döner; sadece bizim taktığımız (kalıcı veri işareti) geri alınır, slot doluysa eyer atta kalır. Havada sağ tıkla eyer slota gider. DV slot ekranı etiketli 2'li sütun düzenine geçti (4 slota yer). Domuz/Strider eyersiz binilemediği için yok. Kullanıcının fikri, 2026-10-25 | — | ikisi | ✅ oyunda denendi |
| M10 | **DV Map** (`dvmap`) | Ayarlanabilir mini harita (köşe, kare/daire, boyut, yakınlaştırma, oyuncuyla dönme, koordinat/biyom/saat satırları, chunk ızgarası, varlık radarı: oyuncu/düşman/hayvan/eşya) ve tam ekran dünya haritası (`M` tuşu, sürükleme, tekerlekle yakınlaştırma). Yüzey (biyom renkleri, yükseklik ve su derinliği gölgesi, meşale/lav aydınlatması) ve mağara katmanı (`AUTO`/`SURFACE`/`CAVE`); keşfedilen bölgeler dünya ve boyut başına PNG olarak diske kaydedilir. İşaret noktası (waypoint) sistemi (`B` tuşu veya haritada sağ tık): dünyada ışık huzmesi ve mesafe etiketi, son ölüm noktasını otomatik işaretleme, yaratıcı/yetkili modda ışınlanma. `MapOverlayHook` ile dış mod katmanı desteği | JourneyMap / Xaero's Minimap | istemci | 🔄 0.1.0 yazıldı, oyunda denenecek |
| M11 | **DV Team** (`dvteam`) → **klan sistemi** | Ayrıntılı tasarım: `docs/KLAN-TASARIM.md`. Klan kurmadan bölge yok; ad, kısaltma, serbest renk, sancak tarzı bayrak; lider/subay/üye. Yerleşimler: başkent (ilk chunk), köyler, ticaret merkezi; her biri adlı, haritada simge + ad (başkentte bayrak). Yerleşim başına müttefik/herkes izinleri (inşa, kapı, sandık, hayvan, tarım, köylü ticareti). Savaş: hazırlık → PvP + baskın (ticaret merkezi ve başkent çekirdeği korunur, savunan çevrimdışıyken baskın yok), barış/teslim/süre ile biter. İttifak teklif/kabul. Sunucu sınırları (chunk, köy, ticaret merkezi, köy boyutu, yüklü chunk, klanlar arası boşluk, savaş süreleri). Haritadan (M → Bölge) al/bırak/yüklü tut; komut `/klan`. Kullanıcının fikri, 2026-10-27 | FTB Teams + Chunks, Towny | ikisi | 🔄 tek oyuncuda denendi (klan kurma, bayrak, yerleşimler, harita; 2026-10-27); çok oyunculu kısım (rütbe, ittifak, savaş, izinler) denenmedi |

M5 en büyük iş: kayıt, sunucu senkronu, envanter ekranı, elytra uçuşu için mixin.

## Kararlar
- Lisans **MIT**; yayın **Modrinth + CurseForge**; git yerel, GitHub gizli depo kullanıcı açınca (2026-10-25).
- Hepsini içeren tek "DV Essentials" paketi şimdilik yok.
- Hiçbir vanilya dokusu dağıtılmaz: DV Food kenarı ve DV Dark çalışma anında üretir.

## Açık
- Ekran görüntüleri, sürüm adı (şimdilik 0.1.0). İkonlar: 32×32 piksel sanatı (tools/make-icons.ps1). DV Lib: tüplü monitör (ekranda dik ":3" kedi yüzü) ve kitaplardan kasa; diğer modlarda köşe işareti yok. Zoom teleskop. Kullanıcının tarifi.
