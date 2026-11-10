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
- **Sürüm:**
  - DV Lib'e bağlı modlar `x.y.z.a`: `x` DV Lib'in ilk sayısı, `y` güncelleme, `z` hata ayıklama ve basit güncelleme, `a` hata düzeltme. `x` değişince DV Lib'e bağlı bütün modlar aynı `x` ile yeniden çıkar. Kullanıcının kararı, 2026-11-06.
  - DV Lib'in kendisi ve DV Lib'e bağlı olmayan modlar (DV New Crafts) `x.y.z`: büyük sürüm, güncelleme, hata düzeltme. Kullanıcının kararı, 2026-11-07.
  - İlk yayın hepsinde `1.0.0.0` olarak çıktı. DV Lib ve DV New Crafts bundan sonra `1.0.0`'dan devam eder; sonraki sürüm (`1.0.1`) yine de `1.0.0.0`'dan büyük sayılır.
- **DV Lib'e eklenecek (fikir, 2026-11-07):** DV menüsünde kurulu olmayan DV modları da listelensin ve nereden indirileceği (Modrinth, CurseForge) gösterilsin. Sayfa adresleri onaylanınca eklenecek.

## Modlar

| # | Mod | Ne yapar | Benzediği | Taraf | Durum |
|---|---|---|---|---|---|
| — | **DV Lib** (`dvlib`) | Ortak kod | FTB Library | ikisi | ✅ 1.0.0.0 |
| M1 | **DV Zoom** (`dvzoom`) | C'ye basılı tutunca yakınlaştırma (varsayılan 4×), tekerlekle 1×–50×, yumuşak geçiş, sinematik kamera | OptiFine zoom | istemci | ✅ 1.0.0.0, oyunda denendi |
| M2 | **DV Food** (`dvfood`) | Doygunluk kadar butun çevresinde altın kenar; elde yemek varken kazanılacak açlık/doygunluk ve doğal yenilenmeyle dolacak can yanıp söner; çubuğun arkasında yorgunluk (exhaustion) şeridi; tooltip'te açlık ve altın kenarlı doygunluk ikonları; F3'te açlık/doygunluk/yorgunluk satırı. Sunucuda da kuruluysa doygunluk/yorgunluk her değiştiğinde senkronlanır. Altın kenar, vanilya `icons.png`'den çalışma anında çıkarılır (doku dağıtılmaz, doku paketleriyle uyumlu) | AppleSkin | ikisi (iki tarafta da isteğe bağlı) | ✅ 1.0.0.0, oyunda denendi |
| M3 | **DV Lens** (`dvlens`) | Ekranın üst ortasında tooltip tarzı kutu: bakılan blok/varlığın ikonu, adı, modu; gereken alet ve seviye (✔/✘ elindekiyle kırılır mı); ekin büyümesi; canlı canı; at/eşek/katır/deve hızı (blok/sn, hız × 42.16) ve zıplama yüksekliği (blok, wiki eğrisi; devede yok, lamada hiç yok); yerdeki eşya ve sayısı. Aç/kapa tuşu (varsayılan atanmamış) | Jade | istemci | ✅ 1.0.0.0, oyunda denendi (alet satırı sadece doğru alet gerektiren bloklarda: "En az: Taş Kazma") |
| M3.1 | DV Lens sunucu bilgileri | Sandık/fırın içeriği, fırın ilerlemesi, spawner vb. (sunucu paketi gerekir) | Jade | ikisi | ⏳ |
| M4 | **DV Vein** (`dvvein`) | ` tuşu basılıyken kırılan bloğa (çaprazlar dahil) bağlı aynı bloklar da kırılır; `forge:ores/*` etiketini paylaşan cevherler birlikte (demir + derin demir). Altın çizgili önizleme ve "Damar: N blok" sayacı. Sunucu ayarı: en çok 64 blok, blok başına ek açlık; alet kırılmak üzereyken ya da açlık bitince durur; Hayatta Kalma'da doğru alet yoksa çalışmaz (Yaratıcı'da her blokta çalışır). Düşenler ve XP doğrudan envantere gelir (sığmayan ayağının dibine düşer). Kırma `ServerPlayerGameMode.destroyBlock` ile (koruma modları, aşınma, istatistik vanilya gibi) | FTB Ultimine | ikisi | ✅ 1.0.0.0, oyunda denendi |
| M4.1 | DV Vein şekilleri | Vein tuşu basılıyken fare tekerleği şekil seçer (nişangâh altında yazar): Damar, Küçük tünel 1×1, Maden tüneli (aşağı inen merdiven), Büyük tünel 3×3, 3×3 yüzey, Kaçış tüneli (yukarı çıkan, 3 blok yüksek merdiven). Boşluğa gelince durur, bağlı olmayan uzak blokları kırmaz. Şekilli modlar blok türü aramaz; konteyner, sıvı, kırılamaz ve (Hayatta Kalma'da) aletle düşmeyen blokları atlar. Yön bakışa göre (`Direction.orderedByNearest`), kaçış tüneli yatay bakışa göre. `VeinShape` istemci önizlemesi ve sunucuda ortak; ağ sürümü 2 | FTB Ultimine | ikisi | ✅ oyunda denendi (2026-10-27) |
| M4.2 | DV Vein: hasat ve kırpma birleşti | DV Harvest DV Vein'in içine taşındı (kullanıcının kararı): sağ tıkla hasat aynen, vein tuşu + sağ tık tüm tarlayı envantere hasat eder. Vein tuşu basılıyken bir koyunu kırpınca 32 blok yarıçapta (64 çap), 3 üst 3 alt tüm koyunlar kırpılır, yün envantere gelir; makas kırılmak üzereyken ya da açlık bitince durur. Damar kırmada da düşenler ve XP envantere gelir | FTB Ultimine + Right Click Harvest | ikisi | ✅ oyunda denendi (2026-10-27) |
| M5 | **DV Slots** (`dvslots`) | Oyuncuya kayıtlı DV slotları (capability). İlk slot **Sırt**: elytra (ya da `dvslots:back` etiketli eşya) göğüs zırhıyla aynı anda. Envanterde "DV" düğmesi ve tuş (varsayılan atanmamış) DV slot ekranını açar; elinde elytra ile sağ tık doğrudan sırt slotuna takar. Uçuş 3 Mixin ile (`LivingEntity.updateFallFlying`, `Player.tryToStartFallFlying`, `LocalPlayer.aiStep` içindeki `getItemBySlot(CHEST)`), aşınma sırttaki elytra'ya işler. Sırtta görünür (cape/skin elytra dokusu dahil), diğer oyunculara senkronlanır. Ölümde keepInventory kapalıysa düşer | Curios / Elytra Slot | ikisi | ✅ 1.0.0.0, oyunda denendi |
| M5.1 | DV Slots ek slotlar | Yeni slot türleri (ör. tılsım/yüzük), Mending'in sırttaki elytra'yı onarması, pelerin ile elytra'nın üst üste binmesini önleme | Curios | ikisi | ⏳ |
| M6 | **DV Dark** (`dvdark`) | `textures/gui/` altındaki dokular yüklenirken gri pikseller koyulaştırılır (renkliler korunur, ton sırası korunduğu için kabartmalar bozulmaz); başka modların arayüzleri de dahil. HUD ikonları ve menü logosu hariç. Ana menü arka planı (panorama) yüklenirken geceye çevrilir: mavi gökyüzü yıldızlı lacivert, gün batımı soluk mor, güneş yerine kare ay, kara ay ışığında koyu mavimsi (`NightSky`, `nightPanorama` ayarı; kullanıcının isteği, 2026-10-30). Koyu gri etiket yazıları (0x404040) ekran açıkken açık griye çevrilir. Doku dağıtılmaz. Aç/kapa tuşu dokuları yeniden yükler. 2 Mixin: `SimpleTexture.load` (NativeImage STORE), `GuiGraphics.drawString` (vanilya int sürümleri) | Dark Mode Everywhere | istemci | ✅ 1.0.0.0, oyunda denendi |
| M7 | **DV Lib ortak parçalar** | Ana ekranda ve Esc menüsünde ("Seçenekler"in solunda) **DV** düğmesi → "DV Modları" menüsü (kurulu her DV modu bir düğme). Ortak ayar ekranı: Mods listesindeki **Config** düğmesi de aynı ekranı açar; açık/kapalı düğmeleri, sayı kutuları (geçersiz değer kırmızı, Bitti kapanır), açıklamalar tooltip'te, TR/EN etiketler. Sunucu ayarları (DV Vein) yalnızca tek oyunculu dünya içinde. Ayar ekranının altında modun tuşları (`key.<modid>.*`): tıkla, yeni tuşa/fare tuşuna bas, Esc boşaltır, çakışan kırmızı; hemen kaydedilir. DV Slots menüde sadece tuşuyla. `DVTheme`: DV Dark açıkken düz renkle çizilen ekranlar (DV Slots) da koyulaşır | — | istemci | ✅ oyunda denendi |
| M8 | İlk yayın | MIT lisansı, Modrinth + CurseForge, yerel git (gizli GitHub sonra). Forge ve NeoForge 1.20.1 (47.1.106) ayrılmış sunucularda test edildi. Sayfa metinleri `docs/YAYIN.md` | — | — | 🔄 yükleme kullanıcıda; ikonlar hazır (tools/make-icons.ps1), ekran görüntüsü eksik |
| M9 | **DV Harvest** → 2026-10-27'de DV Vein'e katıldı (`dvvein`, etiketler `dvvein:harvestable` / `dvvein:blacklist`) | Olgun ekine sağ tık (ana el, eğilmeden): ürün düşer, ekin yaş 0'a döner (diğer özellikleri korunur), düşenlerden bir tohum eksilir. Vanilya + `CropBlock` türevi mod ekinleri, Nether siğili, kakao; diğerleri `dvvein:harvestable` etiketiyle, istenmeyenler `dvvein:blacklist` ile. Fortune işler, bölge koruma (`mayInteract`/`mayBuild`) gözetilir, blok kırma kadar açlık. Sunucuda yoksa istemci tıklamayı yutmaz (boş kanal ile varlık kontrolü). **DV Vein ile:** vein tuşu basılıyken sağ tık, bağlı aynı türdeki tüm olgun ekinleri hasat edip yeniden diker (DV Vein'in sınırı ve açlık ayarıyla; düşenler tıklanan ekinde toplanır). DV Vein isteğe bağlı (compileOnly + ModList kontrolü). Kullanıcının fikri, 2026-10-25 | Right Click Harvest | ikisi | ✅ 1.0.0.0 oyunda denendi (tek ekin ve Vein ile toplu hasat) |
| M5.2 | **DV Slots eyer slotu** | **Eyer** slotu (`dvslots:saddle` etiketi). Evcil, eyersiz at/eşek/katır/deveye binince slottaki eyer takılır (atın 400 yuvası), inince geri slota döner; sadece bizim taktığımız (kalıcı veri işareti) geri alınır, slot doluysa eyer atta kalır. Havada sağ tıkla eyer slota gider. DV slot ekranı etiketli 2'li sütun düzenine geçti (4 slota yer). Domuz/Strider eyersiz binilemediği için yok. Kullanıcının fikri, 2026-10-25 | — | ikisi | ✅ oyunda denendi |
| M10 | **DV Map** (`dvmap`) | Ayarlanabilir mini harita (köşe, kare/daire, boyut, yakınlaştırma, oyuncuyla dönme, koordinat/biyom/saat satırları, chunk ızgarası, varlık radarı: oyuncu/düşman/hayvan/eşya) ve tam ekran dünya haritası (`M` tuşu, sürükleme, tekerlekle yakınlaştırma). Yüzey (biyom renkleri, yükseklik ve su derinliği gölgesi, meşale/lav aydınlatması) ve mağara katmanı (`AUTO`/`SURFACE`/`CAVE`); keşfedilen bölgeler dünya ve boyut başına PNG olarak diske kaydedilir. İşaret noktası (waypoint) sistemi (`B` tuşu veya haritada sağ tık): dünyada ışık huzmesi ve mesafe etiketi, son ölüm noktasını otomatik işaretleme, yaratıcı/yetkili modda ışınlanma. `MapOverlayHook` ile dış mod katmanı desteği | JourneyMap / Xaero's Minimap | istemci | 🔄 1.0.0.0 yazıldı, oyunda denenecek |
| M11 | **DV Team** (`dvteam`) → **klan sistemi** | Ayrıntılı tasarım: `docs/KLAN-TASARIM.md`. Klan kurmadan bölge yok; ad, kısaltma, serbest renk, sancak tarzı bayrak; lider/subay/üye. Yerleşimler: başkent (ilk chunk), köyler, ticaret merkezi; her biri adlı, haritada simge + ad (başkentte bayrak). Yerleşim başına müttefik/herkes izinleri (inşa, kapı, sandık, hayvan, tarım, köylü ticareti). Savaş: hazırlık → PvP + baskın (ticaret merkezi ve başkent çekirdeği korunur, savunan çevrimdışıyken baskın yok), barış/teslim/süre ile biter. İttifak teklif/kabul. Sunucu sınırları (chunk, köy, ticaret merkezi, köy boyutu, yüklü chunk, klanlar arası boşluk, savaş süreleri). Haritadan (M → Bölge) al/bırak/yüklü tut; komut `/klan`. Kullanıcının fikri, 2026-10-27 | FTB Teams + Chunks, Towny | ikisi | 🔄 tek oyuncuda denendi (klan kurma, bayrak, yerleşimler, harita; 2026-10-27); çok oyunculu kısım (rütbe, ittifak, savaş, izinler) denenmedi |
| M12 | **DV Grave** (`dvgrave`) | Ölünce düşen her şey ve XP bir mezar taşı bloğunda saklanır; vanilya envanteri, DV Slots, Curios (kozmetik dahil) ve Cosmetic Armor Reworked yuvaları dahil, başka modların düşürdükleri de yakalanır. Sağ tık envanter düzeninde bir ekran açar, tek düğme her şeyi eski yerine koyar. İlk 30 dakika yalnızca sahibi ve (DV Team ile) klanı açabilir. Kırılmaz, patlamaya dayanıklı, boşalınca kaybolur; adı taşın önünde yazılı. XP ayarı: hepsi / vanilya kadarı / hiç | Corpse, Gravestone | ikisi | ✅ 1.0.0.0 |
| M13 | **DV Music** (`dvmusic`) | Çalan şarkıyı görüp değiştirme (U ya da DV menüsü): ilerleme çubuğu, geri/oynat-duraklat/ileri, arama, ses. Kendi `.ogg` şarkıların `<oyun klasörü>/dvmusic` klasöründen; alt klasörler durum listesi olur (menü, dünya, yaratıcı, su altı, Nether, End). Çalma modu: karışık / sadece Minecraft / sadece benimkiler. Vanilya şarkıları resmi adları ve bestecileriyle; Esc menüsünde küçük oynatıcı | — | istemci | ✅ 1.0.0.0 |
| M14 | **DV Backpack** (`dvbackpack`) | Deriden netherite'e 6 seviye çanta (27–135 yuva, seviye atlayınca içindekiler korunur); DV Slots'un çanta yuvasında, göğüste ya da Curios sırt yuvasında giyilir, yere de konur. Geliştirme kartları sağdaki sekmelerde: çalışma masası, fırın/tütsüleyici/yüksek fırın (çanta nerede olursa pişirir), toplama + mıknatıs, yok etme, yemek, tamamlama (9'luk süzgeç), 8 kovalık tank. Uyku tulumu (doğma noktasını değiştirmez). İçerik dünyada saklanır (`BackpackStorage`). Başka modların `dvbackpack:upgrades` etiketli eşyaları da yuvaya takılır; kendi yuvaları (item handler) varsa panelde açılır | Sophisticated Backpacks, Traveler's Backpack | ikisi | ✅ 1.0.0.0, oyunda denendi |
| M15 | **DV New Crafts** (`dvnewcrafts`) | Normalde bulunan eşyalara tarif: eyer, ad etiketi, demir/altın/elmas at zırhı, zincir zırh. Taş ile demir arası bakır: aletler, zırh, bakır parçası, yeşil alevli meşale ve fener, zincir, parmaklık. Eşyalar oyunun kendi yaratıcı sekmelerinde. DV Lib'e bağlı değil | — | ikisi | ✅ 1.0.0.0, oyunda denendi |
| M16 | **DV Science of Souls** (`dvsouls`) | Ruh şişesiyle her canlıyı yakala (boss'lar hariç), bırak. **Bağ Sunağı:** canlının şişesi + canının yarısı kadar ruh → sana bağlı evcil (takip/bekle/dolaş, sahibini korur). **Ruh Tasması:** ölen evcil Tasma Parçası düşürür, **Ruh Sunağı**'nda eşit güçte ruhla geri gelir. **Ruh Birleştirici:** iki ruhtan hazır melez (Yünlü Dana, Tüylü Domuz, Lav Atı, Kurt Tilki) ya da aynı türden daha güçlü ruh. **Ruh Ocağı, Ruh Tütsüleyicisi, Ruh Yüksek Fırını:** yanlarındaki ruh barından yakan iki kat hızlı fırınlar; DV Backpack ile kartları da var. **Ruh barı:** tüm ruh makineleri yandaki bardan yakar (1 kalp = 1 ruh); bar, altındaki yuvaya konan ruh şişesi, saf ruh kovası veya şişesinden ya da borudan dolar. **Ruh Arıtıcı + saf ruh:** ruh şişesi + ametist (8 ruhta bir parça), kalp kalp çeker; yarıda çıkarılan şişedeki canlı kalan canıyla kalır. Kara taş gövde, takılı şişe, çatlayan ametist üstünde akan ruhlar, doluluk penceresi (2026-11-01) → hiçbir canlıya ait olmayan koyu mavi sıvı, içinde ruh yüzleri; kovası normal fırında yanar, şişesi kömürle **Ruh Kömürü** olur, sunaklarda güvenli sunu. **Bozulma:** sunaklara başka türün ruhu sunulursa (diriltme, bağlama) canlı bir ihtimalle saldırgan **Bozulmuş** canlıya döner (kendi şekli, karartılmış, kızıl gözlü; 2026-10-30). Ruhla at güçlendirme. **Ruh Damıtıcı + ruh kartları:** cam deney tüpü (2 cam + kuvars → 4); ruh + ametist boş tüpe damlar → Ruh Tüpü; altın/elmas/netherite ruh kartı (rafında 1/2/3 tüp, çanta kartlarına benzer; 2026-11-01) DV Backpack'te takılı dururken 18 canlının yeteneğini verir (su altı nefes, tırmanma, lavda yürüme, creeper kaçırma…). JEI ve DV Backpack isteğe bağlı. Kullanıcının fikri, 2026-10-29 | Traveler's Backpack (canlı çantalar) | ikisi | ✅ 1.0.0.0, oyunda denendi |

M5 en büyük iş: kayıt, sunucu senkronu, envanter ekranı, elytra uçuşu için mixin.

## Kararlar
- Lisans **MIT**; yayın **Modrinth + CurseForge**; git yerel, GitHub gizli depo kullanıcı açınca (2026-10-25).
- Hepsini içeren tek "DV Essentials" paketi şimdilik yok.
- Minecraft'ın bir eşyasına bağlı bir şeyde oyunun kendi tasarımı ya da ondan türetilen doku kullanılabilir (örneğin ruh kovası kovaya benzer). Oyunda karşılığı olmayan yeni şeyler özgün çizilir. Kullanıcının kararı, 2026-11-06.

## Açık
- Ekran görüntüleri, sürüm adı (ilk yayın 1.0.0.0). İkonlar: 32×32 piksel sanatı (tools/make-icons.ps1). DV Lib: tüplü monitör (ekranda dik ":3" kedi yüzü) ve kitaplardan kasa; diğer modlarda köşe işareti yok. Zoom teleskop. Kullanıcının tarifi.
- **Dokular:** DV New Crafts'ın bakır dokuları oyunun demir dokularından betikle türetiliyor (kullanıcının kararı: böyle kalıyor, uyarı gelirse değiştirilir ya da kaldırılır; 2026-11-05 özgün çizim denendi ve beğenilmedi). DV Science of Souls'ta melez canlılar ve fırın yüzleri 2026-11-05'te sıfırdan çizildi; saf ruh kovası ve Ruh Kömürü oyunun dokuları üstüne katman olarak kalıyor (kullanıcının kararı).

## Sonraki güncellemeler (plan, 2026-11-07)

Kullanıcının oyunda bulduğu hatalar ve istekleri. Nedenler kodda bulundu, henüz düzeltilmedi.

### DV Lib (1.0.2)
- **Hata (✅ 1.0.2, 2026-11-10): menüde bazı modlara tıklanmıyor.** Artık ayarı olmayan kurulu mod bilgi kutusu (`DVModInfoPopup`) açıyor. `DVMenuScreen.ModTile.refresh()` kurulu bir modu yalnızca ayar kaydı (`DVConfigScreen.register`) varsa tıklanır yapıyor. DV New Crafts'ın ayarı olmadığı için tıklanmıyor.
  - Düzeltme: ayarı olmayan kurulu mod tıklanınca kısa bir bilgi kutusu açsın: ne yaptığı, sürümü, sayfası.
  - Ayrıca yalnızca `registerScreen` ile ekran veren bir mod da tıklanabilmeli.
  - Başka mod da tıklanmıyorsa kullanıcı hangisi olduğunu söyleyecek.
- **Kurulu olmayan modlar için indirme bağlantıları (✅ 1.0.2, CurseForge proje numaralarıyla):** `DVMods.PAGES` boştu. Modrinth ve CurseForge sayfaları onaylanınca doldurulacak. Kilitli kutu sayfayı açacak, şimdilik "yakında" yazıyor.

### DV Music (1.0.1.0)
- **Hata (✅ 1.0.0.1, 2026-11-07): duraklatılan şarkı, duraklatan bir ekran (Esc menüsü, M ile dünya haritası) kapanınca kısa süre çalıp duruyordu.**
  - Neden: oyun, duraklatma menüsü kapanınca bütün sesleri yeniden başlatıyor. `MusicPlayer.onClientTick` ise bizim şarkıyı yalnızca her 10 tikte bir yeniden duraklatıyor (`getGameTime() % 10`); şarkı yarım saniyeye kadar çalıyor.
  - Düzeltme: `SoundEngineMixin`, `SoundEngine.resume()` metodunun sonunda bizim şarkıyı yeniden duraklatıyor. İki komut ses iş parçacığında art arda çalıştığı için arada ses duyulmuyor. Yedek olarak her tikte de duraklatılıyor (eskiden 10 tikte bir).
  - Esc menüsünde çalan şarkının devam etmesi bilerek böyle kalıyor (kullanıcı beğendi).
- **Hata: çalma modu (karışık / sadece Minecraft / sadece benimkiler) çalışmıyor gibi.** İki neden var:
  1. Mod değişince yalnızca bir **sonraki** şarkı etkileniyor, çalan şarkı devam ediyor. Düzeltme: çalan şarkı yeni moda uymuyorsa hemen değişsin.
  2. "Sadece benimkiler" seçili ama `dvmusic` klasöründe çalınabilecek `.ogg` yoksa, sessiz kalmamak için Minecraft şarkıları çalıyor. Kullanıcının klasöründe hiç `.ogg` yok. Düzeltme: o durumda ekranda "klasörde şarkı yok" uyarısı görünsün ve klasörü açan bir düğme olsun.
- **✅ (2026-11-10, derlendi, oyunda denenmedi) MP3, WAV, M4A, MP4 ve AAC.**
  - Çözücüler jar'ın içinde, paket adı `com.darkvolfi.dvmusic.lib.*` olarak değiştirilmiş (shadow `relocate`, `audioLibsJar` görevi; sınıflar `processResources` ile moda girer). Mod bağımlılığı yok.
    - MP3: **JavaMP3** 1.0.1 (MIT). ID3v2 etiketi (kapak resmi) çözücüden önce atlanıyor.
    - M4A/MP4/AAC: **JAAD** 0.8.8 (`com.gitlab.9lukas5:jaad`, kamu malı). POM'u bozuk (sürüm "0"), Gradle'da `metadataSources { artifact() }` ile okunuyor. `de.sfuhrm:jaad:0.8.7` bazı mp4'leri açamadı; JCodec'in AAC çözücüsü sessizlik veriyordu.
    - WAV/AIFF/AU: Java'nın kendi okuyucusu.
    - **FLAC yok:** tek Java kütüphanesi jFLAC LGPL (projenin lisansı MIT). Gerekirse kendi çözücümüz yazılır. FLAC, WMA, Opus "çalınamaz" sayılır.
  - `TrackInstance.getStream` (Forge) kendi dosyalarımızı doğrudan klasörden açar; Ogg değilse `DecodedStream` PCM verir. Eski `CustomMusicPack` kaynak paketi kaldırıldı. Açılamayan dosya sessiz akış verir, çalar bir sonrakine geçer.
  - Süre dosya başlığından (`AudioDecoders.seconds`): MP3 çerçeve başlıkları, MP4 film süresi, AAC çerçeve sayısı.
  - Ölçüm (oyun dışı test): 7 dk'lık MP4 başlıktan 0.7 sn'de, tamamı 8 sn'de çözüldü (gerçek zamanın ~50 katı).
- **✅ (2026-11-10) Müzik klasörü sade: içinde sadece şarkılar.** Alt klasör açılmıyor, eskiden açılan boş durum klasörleri siliniyor (içinde şarkı varsa yerinde kalır, o yerde çalmaya devam eder). Ekranda yazılı "Müzik klasörü" düğmesi. Dosyalar müzik penceresine sürüklenip bırakılabiliyor (klasöre kopyalanır, açık kategoriye eklenir).
- **✅ (2026-11-10) Kategoriler oyun içinden (kullanıcının kararı: çalma listesi + yer).** Klasörle değil: `config/dvmusic-categories.json`.
  - Kategori aç, Minecraft'ın ya da senin şarkılarını kutucukla seç. Kategoriye "nerede çalsın" (Menü, Dünya, Yaratıcı, Su altı, Nether, End) verilebilir: şarkılar oralarda da çalar, senin şarkıların sadece oralarda çalar.
  - "Şarkılar" menüsünden bir kategori seçilirse sadece o kategori çalar (her yerde).

### DV Map (1.0.1.0)
- **✅ (2026-11-10) İstek: kendi işaret kategorilerimiz.**
  - Şu an kategoriler sabit bir listede (`Waypoint.Category`: General, Home, Mine, Village, Farm, Portal, Danger, Explore). İşaret listesi "My Waypoints / Automatic / Structures" bölümleriyle gruplanıyor.
  - Plan: oyuncu kendi kategorisini açabilsin: ad, renk ve isteğe bağlı simge. İşaret listesinde her kategori kendi açılır-kapanır bölümü olur.
  - Liste başlığındaki **+** yanına "yeni kategori" eklenir. Bir işaret düzenleme ekranından ya da sağ tıkla başka kategoriye taşınabilir.
  - Hazır kategoriler başlangıç olarak kalır; silinirse içindeki işaretler General'a geçer.
  - Kategoriler işaretlerle aynı dosyada, dünya başına saklanır.
- **✅ (2026-11-10) İstek: haritada sağ tık bir seçenek menüsü açsın.** Şu an `WorldMapScreen.mouseClicked` sağ tıkta doğrudan yeni işaret ekranını açıyor. Yeni menü:
  - Buraya işaret koy (bugünkü davranış).
  - Buraya ışınlan: yalnızca yaratıcı modda ya da yetkiliyken, işaretlerdeki ışınlanma gibi.
  - Konumu sohbette paylaş: "x, y, z (boyut)" mesajı. Alan oyuncuda DV Map varsa mesaja tıklayınca işaret olarak eklenir.
  - Koordinatları panoya kopyala.
  - Mevcut bir işaretin üstünde: düzenle, ışınlan, paylaş, kategoriye taşı, sil.
  - DV Team'in harita kancaları (`MapOverlayHook`) da bu menüye kendi seçeneklerini ekleyebilsin (ör. bu chunk'ı sahiplen).
- **✅ (2026-11-10) İstek: farenin üstündeki blok haritada işaretlensin.** Chunk ızgarası gibi, imlecin altındaki bloğun karesine ince bir çerçeve çizilir. Uzak yakınlaştırmada blok çok küçük kalınca chunk'ın çerçevesi de hafifçe gösterilebilir. Koordinatlar alttaki satırda zaten yazıyor.
- **✅ (2026-11-10) İstek: Create'in rayları ve trenleri haritada görünsün.** Başka harita modlarında olduğu gibi.
  - Raylar kendi rengiyle, haritanın üstünde ince bir çizgi olarak çizilir. Varsayılan renk belirgin bir turuncu ya da sarı olur ve ayarlardan değiştirilebilir.
  - Trenler simge olarak görünür ve hareket ettikçe yeri güncellenir. İmleç üstüne gelince trenin adı yazar.
  - Create'e bağımlılık olmaz. Create kurulu değilse bu kısım hiç çalışmaz. Raylar blok kimliğiyle (`create:track`), trenler varlık türüyle tanınır; Create'in koduna dokunulmaz.
  - Yalnızca istemcinin yüklediği chunk'lardaki raylar ve trenler görünür. Uzaktaki trenleri görmek sunucudan bilgi ister; o kısım sonraya kalır.
  - Ayar: "Create rayları ve trenleri" aç/kapa.
  - Oyunda denendikten sonra (2026-11-10, kullanıcı): raylar koyu kenarlı kalın çizgi, trenler kapladıkları alan kadar koyu kutu (uzaklaşınca büyük tren simgesi). Kullanıcının örnek gösterdiği harita modundaki gibi.
  - Bilinen sınır: Create'in virajlı rayı iki uçtaki blok arasında tek parça; aradaki yerlerde blok yok, haritada virajda boşluk kalabilir. Kapatmak için virajın eğrisini okumak gerekir (Create'in blok verisinden, sınıflarına dokunmadan); denenecek.
- **✅ (2026-11-10, ilk hali) İstek: ray hattına ve trene tıklayıp ad ve renk vermek** (kullanıcının isteği, 2026-11-10). Sağ tık menüsünde "Bu ray hattına ad ver" ve "Bu trene ad ver"; kayıt `RailLabels` ile `dvmap/<dünya>/rails.json`. Tren kimliği Create'in kayıt verisindeki `TrainId`, okunamazsa vagonun kendi kimliği.
  - Dünya haritasında bir raya ya da trene tıklayınca küçük bir menü: ad ver, renk seç.
  - Ray hattı = birbirine bağlı ray blokları. Ad ve renk hattın tamamına uygulanır; hat haritada o renkte çizilir, adı imleç üstünde yazar.
  - Tren adı ve rengi trenin üstünde ve imleç üstünde görünür. Create'in kendi tren adı varsa varsayılan o olur.
  - Önce yalnız bu oyuncunun haritasında, dünya başına saklanır. Sunucuda herkesle paylaşmak DV Team'in klan demiryolları ile gelir.
  - Açık soru: bir hattı tanımak için hattın bir bloğu saklanır; hat değişince (ray eklenip sökülünce) ad doğru hatta kalmalı.

### DV Team (sonraki)
- **İstek: klan demiryolları** (kullanıcının isteği, 2026-11-10).
  - Bir klan, ray hatlarını ve trenlerini kendi adıyla sahiplenir. Klan üyelerinin haritasında hat klan rengiyle ve adıyla görünür; diğer oyuncular da kimin hattı olduğunu görür.
  - Ad ve renk sunucuda saklanır, klan üyeleri arasında paylaşılır. Yalnız yetkili klan üyeleri değiştirebilir.
  - Ayrıntılar (başka klanın hattını kullanma, istasyon izinleri, savaşta durum) uygulamadan önce kullanıcıyla konuşulacak.

### DV Science of Souls (1.0.1.0)
- **✅ (2026-11-10) İstek: ruh kartı tarifinde kağıt yerine DV Backpack'in boş kartı (`dvbackpack:upgrade_base`).**
  - Kartlar zaten DV Backpack'in kart yuvasına takılıyor, aynı tabandan yapılmaları tutarlı olur.
  - DV Backpack isteğe bağlı olduğu için tarif iki yollu olacak: DV Backpack kuruluysa boş kart, değilse kağıt. Forge'un `forge:mod_loaded` koşuluyla, DV Backpack'in kendi kartlarının tarifleri gibi.
  - Dosya: `recipes/gold_soul_charm.json` (kağıt yalnızca altın kartta var; elmas ve netherite kartlar ondan yükseltiliyor).
- **✅ (2026-11-10) İstek: Ruh Kömürü Bloğu.**
  - Kömür bloğu gibi: 9 Ruh Kömürü → 1 blok, blok → 9 Ruh Kömürü.
  - Yakıt süresi Ruh Kömürü'nün 9 katı (3200 × 9 = 28800 tik); kömür bloğu da kömürün tam 9 katı.
  - Doku özgün çizilir ama kömür bloğuna benzer (kullanıcının kuralı: Minecraft eşyasına bağlı tasarım vanilyadan türetilebilir), ruh kömürünün koyu mavi tonlarında.
  - Yaratıcı sekmesine, kırma etiketine (kazma) ve kendi düşüş tablosuna eklenir.
- **✅ (2026-11-10) İstek: Ruh Tasması her canlıya takılsın ve tek kullanımlık olsun.**
  - Şu an: `SoulEvents.collar` tasmayı yalnızca oyuncunun evcil hayvanına (Bağ Sunağı'nda bağlanmış ya da evcilleştirilmiş) takıyor; sahibi yoksa "not_bound" diyor. Ölünce düşen parçadaki ruh (`Soul.of`) canlının kalıcı verisini, tasma işareti dahil, taşıyor. Bu yüzden diriltilen canlı tasmasıyla geri geliyor ve tasma sonsuza kadar işe yarıyor.
  - Yeni:
    - Tasma sahibi olmayan herhangi bir canlıya takılabilir. Boss'lar ve `DVSouls`'taki yasak liste hariç; başkasının evcil hayvanına takılamaz.
    - Tasmayı takan oyuncu canlının kalıcı verisine "tasma sahibi" olarak yazılır. Parçayı Ruh Sunağı'na yalnızca o oyuncu koyabilir; evcil hayvanlarda bu zaten sahibi.
    - **Tek kullanımlık:** canlı ölünce düşen parçaya kaydedilen ruhtan tasma işareti silinir. Diriltilen canlı tasmasız gelir; bir daha diriltilmek için yeni tasma gerekir.
    - Evcil olmayan bir canlı diriltilince eskisi gibi gelir, evcil olmaz. Bağlamak için yine Bağ Sunağı gerekir.
  - Düşman canlılara (zombi, iskelet...) da takılabilir; diriltilince yine düşman olarak gelir (kullanıcının kararı, 2026-11-08).

### DV New Crafts (1.1.0)
- **İstek: zombi etinden deri.**
  - Tarif: ortada makas, çevresinde 8 çürük et → 8 deri. Makas tezgâhta kalır ama canı azalır.
  - Vanilya tarifleri aleti aşındıramıyor; aleti geri verip canını azaltan küçük bir tarif türü yazılacak (`ShapedRecipe`'ten türeyen, `getRemainingItems` metodunda makası hasarlı olarak geri veren bir sınıf ve onun serializer'ı). DV New Crafts'ın ilk kodlu tarifi olur.
  - Makasın canı 0'a inerse kırılır, vanilya aletleri gibi; Unbreaking büyüsü hasarı azaltır.
  - JEI'de normal tarif olarak görünür.
  - Makas bir tarifte 8 can kaybeder, her deri için 1 (kullanıcının kararı, 2026-11-08). Makasın canı 238, yani yaklaşık 30 tarif ya da 240 deri.
- **İstek: oyun içinden tarif ekleme (yalnızca yöneticiler).** Kullanıcının isteği, 2026-11-08.
  - Ayarlardaki arayüzde bir "Tarifler" ekranı olacak. Burada yeni tarif eklenir, eklenen tarifler düzenlenir ya da silinir.
  - Tarif türleri: şekilli (3×3 ızgara), şekilsiz, fırın, tütsüleyici, yüksek fırın, kamp ateşi, taş kesici.
  - Eşyalar oyuncunun envanterinden ya da arama listesinden ızgaraya sürüklenir. Sonuç eşyası ve adedi seçilir.
  - **Yalnızca yönetici:**
    - Ekran yalnızca sunucuda yetki seviyesi 2 ve üstü (op) olan oyuncuya açılır. Tek oyunculuda hile açıksa açılır.
    - Kayıt isteği sunucuda da yetki kontrolünden geçer. Yetkisiz bir istemci paket gönderse bile tarif eklenmez.
  - Tarifler dünyanın içinde bir veri paketi olarak saklanır: `world/datapacks/dvnewcrafts_custom/data/dvnewcrafts_custom/recipes/*.json`. Kaydedince `/reload` gibi yeniden yüklenir ve bütün oyunculara gider. Dünya kopyalanınca tarifler de gider. Vanilya biçiminde olduğu için mod kaldırılsa bile JSON'lar okunabilir kalır.
  - Mevcut bir tarifi kapatma (ör. DV New Crafts'ın ya da oyunun bir tarifi) ikinci aşama.
  - Teknik:
    - DV New Crafts şu an DV Lib'e bağlı değil ve ayar ekranı yok. Ekranı DV Lib'in arayüz parçalarıyla yapmak için DV Lib isteğe bağlı bağımlılık olur (`compileOnly` + `ModList` kontrolü); DV Lib yoksa ekran Mods listesinden açılan basit bir ekran olur.
    - Kendi ağ kanalı: tarifi kaydet, sil, listele.
    - JEI yeni tarifleri `/reload` sonrası kendisi görür.

### DV Lens (1.0.1.0)
- **Hata (✅ 1.0.0.1, 2026-11-08): "Show where mobs can spawn" tuşu iki kez görünüyordu, "Toggle DV Lens" hiç yoktu (Kontroller ekranında da).**
  - Neden: Forge'un olay sistemi bir dinleyiciyi `paket.__SınıfınKısaAdı_metot_Olay` adıyla üretiyor ve bu adla önbelleğe alıyor (eventbus 6.2, `IEventListenerFactory.getUniqueName`). `LensOverlay.ModEvents.onRegisterKeys` ile `LightMarkers.ModEvents.onRegisterKeys` aynı pakette, aynı adda, aynı olayda. İkincisi birincinin dinleyicisini kullandı: ışık tuşu iki kez kaydedildi, aç/kapa tuşu hiç kaydedilmedi.
  - Düzeltme: `LightMarkers` içindeki metodun adı `onRegisterLightKey` oldu. Bütün modlar tarandı, başka çakışma yok.
- **İstek (✅ 1.0.0.2, 2026-11-08): kırılma çubuğu akıcı dolsun.** Eskiden çatlak aşamasını (10 adım) gösteriyordu. Artık oyunun `destroyProgress` alanını (SRG `f_105193_`) okuyup kareler arasında bu tikin payını ekliyor; alan okunamazsa eski aşamalı gösterime düşüyor.
- **✅ (2026-11-10) İstek: sandık içeriği ayarı daha kolay bulunsun.**
  - Ayar aslında var: "Container contents, furnaces" (`showContents`). Kullanıcı bunun sandıklar için olduğunu anlamadı.
  - Plan: iki ayara bölünsün. **Sandık içeriği** (sandık, varil, shulker kutusu...) ayrı, **fırın ve demleme** (fırın, demleme standı, yanma ve pişme durumu) ayrı. Adlar açık olsun.

### DV Food (1.0.1.0)
- **✅ (2026-11-10) İstek: zararlı yemek açlık çubuğunda görünsün.**
  - Elde tutulan yemek Açlık (Hunger) etkisi veriyorsa (çürük et, çiğ tavuk...), çubuğun yanıp sönen önizlemesi vanilyanın Açlık etkisindeki yeşil ikonlarıyla gösterilsin.
  - Zehir (Poison) veriyorsa (balon balığı, örümcek gözü, zehirli patates) önizleme zehir görünümünde yanıp sönsün; can barındaki zehir rengi gibi.
  - Etkinin ihtimali yüzde 100 değilse (çiğ tavuk %30) bu da ikon üstünde belli olsun, ya da yalnızca tooltip'te yüzde olarak yazsın. Hangisi olacağına uygularken bakılacak.
  - İkonlar vanilyanın `icons.png` dosyasından alınır (DV Food'un kuralı: doku dağıtılmaz).
- **✅ (2026-11-10) İstek: iksirler de can çubuğunda önizlensin** (kullanıcının isteği, 2026-11-08).
  - DV Food, elde yemek varken kazanılacak canı zaten yanıp söndürüyor. Aynısı iksirlerde de olacak (içilen, fırlatılan ve kalıcı iksirler; okla atılanlar hariç).
  - **Anında Can (Instant Health):** dolacak kalpler yanıp söner; seviye II'de daha fazla.
  - **Anında Hasar (Instant Damage):** gidecek kalpler kırmızı-siyah yanıp söner. Ölümsüz canlılarda ters çalıştığı için yalnızca oyuncuya göre gösterilir.
  - **Yenilenme (Regeneration), Zehir, Solma (Wither):** süre boyunca dolacak ya da gidecek toplam can yaklaşık olarak gösterilir. Zehir son yarım kalbi almaz, bu da hesaba katılır.
  - **Emilim (Absorption):** altın elma gibi, eklenecek sarı kalpler önizlenir. Bu, altın elmalar için de eklenir.
  - Hangi etkilerin gösterileceği bir ayarla kapatılabilir.
