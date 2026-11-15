# TV Ana Ekran (kendi launcher'ımız)

Sade, hızlı, reklamsız bir Android TV ana ekranı: üstte saat ve tarih, altta tek sıra büyük uygulama kutuları. Odaklanan kutu büyür, adı altında çıkar. Sondaki "Ayarlar" kutusu TV ayarlarını açar. Reklam, öneri, sponsorlu satır yok.

> Bu proje yazıldı ama bir TV'de ya da derleyicide **denenmedi**. İlk derlemede küçük bir hata çıkarsa hata mesajını sohbete yapıştır, düzeltiriz.

## 1. APK'yı derle (iki yoldan biri)

### Yol A: Bilgisayara hiçbir şey kurmadan (GitHub ile)
1. github.com'da ücretsiz hesap aç (varsa gir).
2. Yeni bir **özel (private)** depo oluştur, adı `tv-ana-ekran` olsun.
3. Bu klasörün **içindekileri** depoya yükle (Add file > Upload files, tüm dosya ve klasörleri sürükle). `.github` klasörü de gitmeli, gizli klasör olduğu için Windows'ta "gizli öğeleri göster"i aç.
4. Depoda **Actions** sekmesine gir, "APK derle" çalışır (2-5 dakika). Yeşil tik olunca çalıştırmanın içine gir, en altta **tv-ana-ekran-apk** dosyasını indir, zip'ten çıkar: `app-debug.apk`.

### Yol B: Android Studio ile
1. Android Studio'yu kur, bu klasörü **Open** ile aç, Gradle eşitlemesi bitsin.
2. Üst menü: Build > Build APK(s). APK şurada çıkar: `app/build/outputs/apk/debug/app-debug.apk`

## 2. TV'ye kur (ADB)
`app-debug.apk` dosyasını `platform-tools` klasörüne koy, cmd'de:
```
adb connect TV_IP:5555
adb install -r app-debug.apk
```
"Success" yazmalı.

## 3. Önce test et (stok ana ekranı henüz KAPATMA)
```
adb shell am start -n com.tvhome.launcher/.MainActivity
```
TV'de yeni ana ekran açılmalı. Kumandanın yön tuşlarıyla gez, OK ile uygulama aç. Çalışıyorsa devam.

## 4. HOME tuşunu bu ekrana bağla
Sırayla, her komutun çıktısına bak:
```
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell cmd package set-home-activity com.tvhome.launcher/.MainActivity
adb shell input keyevent KEYCODE_HOME
```
Sonra TV'yi yeniden başlatıp HOME'a bas. Bu ekran gelmeli.

## Geri alma (bir şey ters giderse)
```
adb shell pm enable com.google.android.tvlauncher
adb uninstall com.tvhome.launcher
```
Uygulamalarına dokunulmaz.

## Kendine göre değiştirme
Dosya: `app/src/main/java/com/tvhome/launcher/MainActivity.java`, en üstteki **AYARLAR** bölümü:
- `PINNED`: başta görünecek uygulamaların paket adları (sıra önemli).
- `HIDDEN`: hiç görünmeyecek uygulamalar.
- `SHOW_NON_TV_APPS`: `false` yaparsan sadece TV uygulamaları listelenir.

**Duvar kağıdı:** `app/src/main/res/` altında `drawable-nodpi` adlı bir klasör oluştur, içine `wallpaper.jpg` koy (adı tam böyle, küçük harf). Yeniden derle. Dosya yoksa koyu mavi gradyan kullanılır.

Paket adlarını bulmak için: `adb shell pm list packages -3`


## Sağ üstteki hızlı butonlar (v2)
Kumandada **yukarı** tuşuna basınca sağ üstteki dört butona geçilir: **Internet** (Wi-Fi/ağ ayarları, bağlantı yoksa ikon soluklaşır), **Bluetooth**, **Arka plan** (her basışta bir sonraki arka plana geçer, seçim hatırlanır), **Ayarlar** (TV ayarları). Aşağı tuşuyla uygulama sırasına dönülür.

Güncellemek için: depoda **Add file > Upload files** ile `app` klasörünü yine sürükle (aynı adlı dosyaların üzerine yazılır), **Commit changes**. Actions yeni APK'yı kendiliğinden derler. İndirip `adb install -r app-debug.apk` ile kur.


## Uygulama sırasını değiştirme (v3)
Bir uygulamanın üzerindeyken kumandadaki **OK tuşunu basılı tut** (yaklaşık yarım saniye). Menü açılır:
- **Taşı (sağ/sol tuşlarıyla):** Taşıma modu. Sağ/sol tuşlarıyla uygulama yer değiştirir, **OK** ile biter. Üstte bilgi kutusu görünür.
- **Başa al:** Uygulamayı tek adımda en başa koyar (en çok kullandığını sondan başa çekmek için en hızlısı).
- **Gizle:** Uygulamayı ana ekrandan gizler (silmez).
- **Gizlenenleri geri getir:** Hepsini geri açar.
- **Sıralamayı sıfırla:** Varsayılan sıraya döner.

Sıra TV'de kayıtlı kalır, yeniden başlatınca bozulmaz. Yeni kurduğun uygulamalar listenin sonuna eklenir.


## Izgara görünümü (v4)
Uygulamalar artık 5'li sütunlar halinde alt alta dizilir (kutu boyutu ekran genişliğine göre kendiliğinden ayarlanır). Aşağı tuşuyla alt satırlara inilir, liste uzunsa ekran kayar. Üst satırdan **yukarı** tuşuyla sağ üstteki butonlara çıkılır.
Taşıma modunda artık **dört yön tuşu** da çalışır: sağ/sol bir adım, yukarı/aşağı bir satır (5 adım) taşır.


## Güncelleme kurulamıyorsa (v5)
GitHub her derlemede farklı bir imza anahtarı üretiyordu, bu yüzden `adb install -r` "signatures do not match / UPDATE_INCOMPATIBLE" hatası verip eski sürümü yerinde bırakıyordu. v5'ten itibaren projedeki sabit `app/debug.keystore` ile imzalanır, sonraki güncellemeler `adb install -r` ile sorunsuz kurulur. v5'e geçerken **bir kere** eskisini kaldırıp kurmak gerekir:
```
adb shell pm enable com.google.android.tvlauncher
adb uninstall com.tvhome.launcher
adb install app-debug.apk
```
(Bu anahtar sadece evde kurulum içindir, bir uygulama mağazasına yüklenmez.)
