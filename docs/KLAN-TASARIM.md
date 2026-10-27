# DV Team → Klan sistemi tasarımı

Durum: **onaylandı ve uygulandı** (2026-10-27), oyunda deneniyor. Kullanıcının seçtikleri ✅ ile, benim önerdiğim varsayılanlar ⚙️ ile işaretli; ⚙️ olanlar değiştirilebilir.

## 1. Klan

- ✅ Klan kurmadan bölge alınamaz. Her oyuncuya kendiliğinden açılan tek kişilik takım **kalkar**; klansız oyuncu "serbest"tir.
- Kurma: Y ekranı → **Klan Kur**
  - **Ad** (3–24 karakter, sunucuda benzersiz)
  - ⚙️ **Kısaltma** (2–4 harf, ör. `[DV]`): sohbette, oyuncu adının yanında ve haritada kısa gösterim için
  - ✅ **Renk:** serbest renk seçici (renk tonu/doygunluk alanı + parlaklık çubuğu + `#RRGGBB` kutusu)
  - ✅ **Bayrak:** Minecraft sancağı gibi. Taban rengi + en çok 6 desen katmanı (vanilya tezgâhtaki desenler ve 16 boya rengi), canlı önizleme
- ⚙️ İstenirse lider klan ekranından bayrağın **gerçek sancak eşyasını** alabilir (bir sancak karşılığında).
- ✅ **Rütbeler:**

| Yetki | Lider | Subay | Üye |
|---|:-:|:-:|:-:|
| Bölgede inşa, sandık, kapı | ✔ | ✔ | ✔ |
| Chunk al/bırak, yüklü tut | ✔ | ✔ | — |
| Davet et, üye at (subay atamaz) | ✔ | ✔ | — |
| Yerleşim kur, adlandır | ✔ | ✔ | — |
| Yabancı/müttefik izinleri, ticaret merkezi ayarları | ✔ | — | — |
| Savaş, barış, ittifak | ✔ | — | — |
| Rütbe ver/al, liderliği devret, klanı dağıt, ad/renk/bayrak | ✔ | — | — |

- Lider klandan çıkamaz; önce liderliği devreder ya da klanı dağıtır. Son üye çıkınca klan dağılır, bölgeleri boşalır.
- Klan sohbeti `/klan msg` (ve `/dvteam msg`) kalır.

## 2. Yerleşimler: şehir (başkent), köy, ticaret merkezi

- ✅ Her klanın **tek bir başkenti** (şehir) vardır. Klanın **ilk aldığı chunk** başkenti kurar; ona ad verilir.
- ✅ **Köyler:** başkentten ve diğer köylerden ayrı bir yerde kurulur.
  - Mevcut bir yerleşime **yüzeyden komşu olmayan** bir chunk alınınca "Yeni köy kur" penceresi açılır; ad verilir.
  - ⚙️ Bir yerleşimin chunk'ları birbirine bitişik olmalı (ada olamaz). Bir chunk'ı bırakmak yerleşimi ikiye bölecekse izin verilmez.
- ✅ **Ticaret merkezi:** haritadan aynı şekilde kurulur, türü "Ticaret merkezi" seçilir.
  - ⚙️ İçinde herkes için **PvP kapalı** (savaş sırasında da güvenli bölge), baskın yapılamaz.
  - Varsayılan yabancı izinleri daha geniş: kapılar, düğmeler, oturma, köylülerle ticaret açık. Sandık ve inşa kapalı, lider açabilir.
- **Bölge kuralları** (sunucu ayarları, ⚙️ varsayılanlar):

| Ayar | Varsayılan |
|---|---|
| Klan başına en çok chunk (tüm yerleşimler toplamı) ✅ | 256 |
| Klan başına en çok köy | 5 |
| Klan başına en çok ticaret merkezi | 1 |
| Köy başına en çok chunk | 32 |
| Klan başına en çok yüklü (force-load) chunk | 8 |
| Farklı klanların yerleşimleri arasında en az boşluk (chunk) | 2 |

- ✅ **İzinler:** her yerleşim için lider iki kitle ayrı ayrı ayarlar: **Müttefikler** ve **Herkes** (klansızlar dahil). Seçenekler:
  - İnşa (blok kırma/koyma, kova)
  - Kapı, kapak, çit kapısı, düğme, kol
  - Sandık, fırın ve diğer kaplar
  - Hayvanlar (vurma, binme, sağma, kırpma)
  - Tarım (hasat etme, tohum ekme; DV Vein'in hasadı da buna uyar)
  - Köylülerle ticaret
- Patlama koruması tüm yerleşimlerde geçerli (sunucu ayarıyla kapatılabilir).

## 3. Haritada görünüm

- Bölge sınırları klan renginde (mevcut), içi çok hafif renkli.
- ✅ Her yerleşimin **adı** kendi bölgesinin ortasında, klan renginde yazar. Uzaklaştıkça küçülür ve önce köy adları gizlenir.
- ✅ **Başkent** daha belirgin: piksel **kale/bina** simgesi, daha büyük ad ve yanında klanın **bayrağı**.
  - Köy: küçük ev simgesi
  - Ticaret merkezi: terazi/kese simgesi
- Mini haritada yerleşim adı ve simgesi küçük boyutta.
- Fare bir chunk'ın üstündeyken: klan [kısaltma], yerleşim adı ve türü, yüklüyse ⚡.

## 4. Savaş

- ✅ **PvP + baskın:**
  - Lider başka bir klana savaş ilan eder.
  - ⚙️ **Hazırlık süresi** 10 dakika: iki klana da duyurulur, bu sürede bir şey değişmez.
  - Savaş başlayınca iki klanın üyeleri birbirine her yerde vurabilir (ticaret merkezleri hariç).
  - Düşman bölgesinde blok kırıp koyabilir, sandık ve kapı açabilir.
- ⚙️ **Korunanlar:** ticaret merkezleri ve **başkent çekirdeği** (başkentin ilk chunk'ı ve çevresindeki 3×3).
- ⚙️ **Çevrimdışı baskın yok:** baskın yalnızca savunan klandan en az bir oyuncu çevrimiçiyken yapılabilir (PvP her zaman açık).
- ⚙️ **Bitiş:**
  - iki lider de barışı kabul ederse
  - bir taraf teslim olursa
  - en çok 2 saat (gerçek zaman) dolunca
  - Sonrasında aynı iki klan arasında 24 saat bekleme süresi
- Müttefikler savaşa otomatik girmez. Savaştaki iki klan müttefikse ittifak bozulur.
- Sunucu ayarları: savaş açık/kapalı, hazırlık, en uzun süre, bekleme, çevrimdışı baskın.
- Savaşın başlaması ve bitmesi tüm sunucuya sohbette duyurulur. Savaştaki klanların bölgeleri haritada kırmızı kesik çizgiyle görünür.

## 5. İttifak

- Mevcut müttefiklik kalır, karşılıklı olmalı: biri teklif eder, öteki kabul eder.
- Müttefiklere dost ateşi klanın kendi ayarına uyar.

## 6. Ekranlar (Y)

- **Klansızken:** Klan Kur düğmesi, gelen davetler (Katıl), herkese açık klan listesi (ad, renk, üye sayısı).
- **Klandayken**, beş sekme:
  1. **Genel:** bayrak, ad, kısaltma, renk, rütben, chunk ve köy sayıları
  2. **Üyeler:** rütbe ver/al, at, liderliği devret
  3. **Yerleşimler:** liste (ad, tür, chunk sayısı); yeniden adlandır, izinleri düzenle, haritada göster
  4. **Diplomasi:** ittifak teklif et/kabul et/boz, savaş ilan et, barış teklif et, teslim ol
  5. **Davet:** çevrimiçi oyunculara davet
- Harita (M) → **Bölge** modu:
  - sol tık al, sağ tık bırak, Shift ile yüklü tut
  - komşu olmayan yere tıklayınca yeni köy veya ticaret merkezi penceresi açılır
  - komutlar (`/klan …`, eski `/dvteam …` de çalışır)

## 7. Uygulama notları

- Veri yeni bir `SavedData`'da (`dvteam_clans`). Eski taslak takım verisi oyunda hiç kullanılmadı, taşınmaz.
- Bayrak sancak desen kimlikleriyle (`minecraft:…` + boya rengi) saklanır; modların eklediği desenler de çalışır.
- Ağ: tüm klan/yerleşim/savaş durumu istemcilere eşitlenir (mevcut `SyncTeamDataPacket` genişler). İşlemler tek bir eylem paketiyle sunucuya gider, yetkiyi sunucu denetler.
- Mod adı "DV Team" kalır, oyun içinde "Klan" denir. ⚙️ Mod adını "DV Clans" yapmak da mümkün.
