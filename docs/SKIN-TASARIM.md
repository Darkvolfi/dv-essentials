# DV Skin — tasarım

Oyuncunun skinini, pelerinini ve elytra dokusunu değiştiren DV modu. Sunucuda da kurulu olunca seçim sunucu üzerinden diğer oyunculara gider; modu olan herkes görür. Kullanıcının fikri, 2026-11-15. Mod kimliği `dvskin`, sürüm 1.0.0.0, DV Lib'e bağlı.

## Kararlar (kullanıcı, 2026-11-15)
- Skin, pelerin ve elytra **tek modda**, tek ekranda.
- Skin kaynakları: **dosya** (bilgisayardan PNG), **Mojang** kullanıcı adı, **Ely.by** kullanıcı adı, **bağlantı** (doğrudan PNG adresi; skin sitelerinden kopyalanan).
- Pelerin: **hazır DV pelerinleri**, **kendi resmi**, gerçek **Mojang pelerini** olan DV pelerini seçmezse onu kullanmaya devam eder.
- Boyut: **yalnız standart** (skin 64×64 / 64×32, pelerin 64×32); HD şimdilik yok. Seçim **her sunucuda ve tek oyunculuda aynı**.
- Ekler: **ince/kalın kol** (Alex/Steve), pelerinden ayrı **elytra dokusu**, seçerken **dönen karakterle önizleme**.
- Görünürlük: sunucuda modu olan herkes.

## Servisler (araştırma, 2026-11-15)
- **Mojang:** `api.mojang.com/users/profiles/minecraft/<ad>` → UUID; `sessionserver.mojang.com/session/minecraft/profile/<uuid>` → base64 `textures` (SKIN/CAPE url, slim ise `metadata.model = "slim"`). Kimlik yok; ~200 istek/2 dk/IP.
- **Ely.by:** `skinsystem.ely.by/textures/<ad>` → JSON (SKIN/CAPE url, slim bilgisi); `/skins/<ad>.png`, `/cloaks/<ad>.png`. Belgesi (docs.ely.by/en/skins-system.html) projelerde skin kaynağı olarak kullanılmasına açık. HD yok.
- **Eklenmeyenler:** MinecraftCapes, TLauncher (belge/şart bulunamadı), NameMC/Skindex/Planet Minecraft (API yok; kullanıcı PNG bağlantısını ya da dosyayı verir).
- Yayından önce Mojang'ın kullanım kuralları elle okunacak (araştırmada sayfa açılmadı).

## Mimari
**İstemci seçer, sunucu dağıtır, sunucu hiçbir adrese bağlanmaz.**
1. Oyuncu ekrandan kaynak seçer. İndirme (Mojang/Ely.by/bağlantı) yalnız oyuncunun bilgisayarında, arka planda yapılır. Sonuç PNG baytlarıdır.
2. Seçim istemcide saklanır (`config/dvskin/`: `skin.png`, `cape.png`, `elytra.png`, `dvskin-client.toml`'da kaynak ve model). Her sunucuda ve tek oyunculuda aynı görünüm.
3. Sunucuya girişte ve seçim değişince istemci PNG'leri gönderir. Paket oyuncu→sunucu yönünde ~32 KB ile sınırlı: veri parçalara bölünür.
4. Sunucu doğrular: gerçek PNG, boyut (skin 64×64 ya da 64×32 → 64×64'e çevrilir; pelerin/elytra 64×32), bayt sınırı, oyuncu başına hız sınırı. Geçerli olanı bellekte tutar ve herkese yollar; yeni giren oyuncuya mevcut olanların hepsi gider.
5. Her istemci gelen PNG'yi `DynamicTexture` olarak kaydeder ve oyuncunun `PlayerInfo`'suna koyar: `textureLocations` (SKIN/CAPE/ELYTRA) ve `skinModel`. Oyun Mojang skinini sonradan indirip üstüne yazabildiği için seçili oyuncular her tikte kontrol edilir. **Mixin yok.**
6. Sunucuda DV Skin yoksa: yalnız oyuncu kendi seçimini görür (tek oyunculu gibi). Modu olmayan oyuncular normal skinleri görür.

## Sunucu ayarları
- `allowSkins`, `allowCapes` (ayrı ayrı, varsayılan açık; kullanıcı kararı).

## DV pelerinleri
- `tools/` altında bir betikle çizilir (elle PNG yok). Kullanıcı kararı: **8 DV temalı** pelerin (her biri bir DV modundan esinli: Souls, Map, Team, Glass, Music, Vein, Backpack, Lib/DV) **ve 16 sade renkli** pelerin (boya renkleri, küçük DV işaretiyle). Oyuncu ayrıca kendi pelerin resmini ekleyebilir.

## Ekran
- DV menüsünden ve bir tuştan açılır. Sol: dönen önizleme (fareyle döndürülür). Sağ: sekmeler Skin / Pelerin / Elytra; her birinde kaynak seçimi ve alanı, Uygula düğmesi, hata mesajı (bulunamadı, geçersiz resim, çok büyük).

## Doğrulama
- Tek oyunculu: her kaynak, ince/kalın kol, pelerin ve elytra, önizleme.
- Çok oyunculu (dev sunucu, iki oyuncu): birinin seçimi diğerinde görünüyor; sonradan giren de görüyor; sunucuda mod yokken çökmüyor; hatalı/çok büyük PNG reddediliyor.
- Embeddium ve DV Slots sırt elytrası ile birlikte.
