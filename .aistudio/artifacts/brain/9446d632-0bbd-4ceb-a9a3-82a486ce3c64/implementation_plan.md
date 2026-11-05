# Akıllı Araç Kamerası & Telemetri HUD Geliştirme Planı

Bu geliştirme planı, kullanıcı onayına istinaden belirlenen geliştirme sırasına göre üç ana aşamayı kapsar: Wi-Fi kamera canlı akış bağlantı kararlılığı ve otomatik teşhis modülü, 0-100 km/s ivmelenme ölçümlü yarış gösterge temaları ve sürüş seyahat kayıt defteri.

---

### Kullanıcı Tercihleri & Karar Özeti

> [!IMPORTANT]
> Kullanıcı "sıradan başlayalım" diyerek önerilen geliştirme adımlarını sırayla uygulamayı tercih etmiştir.

- **1. Öncelikli Aşama (Onaylanan):** Wi-Fi Canlı Yayın Bağlantı Kararlılığı, Ping / TCP Port Doğrulayıcı ve Otomatik Kurtarma Motoru.
- **2. Aşama (Sonraki):** Yeni Yarış/Pist HUD Teması, 0-100 km/s Drag/İvmelenme Ölçüm Sayacı ve Dinamik Devir Işıkları (Shift Lights).
- **3. Aşama (Final):** Sürüş Seyahat Geçmişi (Trip Log), Yakıt ve Telemetri Özeti ile CSV/GPX Dışa Aktarma.

---

## 1. Genel Bakış & Temel Konsept

Uygulamanın temel amacı, araç sürüş esnasında sürücüye gerçek zamanlı OBD-II telemetrisi ve YI Car Dash Cam (192.168.1.254) üzerinden gecikmesiz canlı video beslemesi sunmaktır. Bu geliştirme ile:
1. Donanımsal Wi-Fi bağlantı problemleri (mobil veri çakışması, soket zaman aşımları, RTSP yol uyuşmazlıkları) otomatik olarak tespit edilip tek tuşla çözülecek.
2. Yarış pisti ve performans tutkunları için 0-100 km/s hızlanma sayacı ve dinamik LED devir ışıkları eklenecek.
3. Sürüş tamamlandığında seyahat süreleri, ortalama hız, pik G-Kuvveti ve yakıt tüketimi yerel olarak arşivlenecek.

---

## 2. Kullanıcı Deneyimi & Arayüz Tasarımı

### Kullanıcı Akışı (User Flows)
1. **Akış Bağlantı ve Teşhis Akışı:**
   - Kamera Wi-Fi'ına bağlanıldığında, uygulama arka planda 100ms içinde IP ping ve Port 554/3333/80 soket testi yapar.
   - Durum rozetinde her portun açık/kapalı durumu görsel olarak (yeşil/kırmızı mikro piller) gösterilir.
   - Akış gecikirse veya koparsa, video motoru kesintisiz olarak sonraki RTSP aday yolunu dener ve kullanıcıya akıcı geri bildirim verir.

2. **Yarış HUD & 0-100 İvmelenme Modu:**
   - HUD ekranında tek dokunuşla "Cadde (Street)", "Cyberpunk (Neon)", "Pist (Race Track)" temaları arasında geçiş yapılır.
   - Pist modunda araç 0 km/s'de durduğunda ivmelenme sayacı hazır (READY) durumuna geçer, gaz verildiğinde kronometre otomatik başlar ve 100 km/s'ye ulaşıldığında süreyi (örneğin: 6.42 sn) ekranda kilitler.

3. **Görsel Tema & Renk Paleti:**
   - **Ana Vurgular:** Electric Cyan (`#00E5FF`), Neon Yeşil (`#00E676`), Xiaomi Turuncu (`#FF6900`), Sport Kırmızı (`#FF1744`).
   - **Arka Plan:** Çok katmanlı füme ve cam parlaklığı (`#0B0E14` ve `%85` alfa değerleri).
   - **Tipografi:** Monospace telemetri fontları ile yüksek okunabilirlikli dijital göstergeler.

---

## 3. Ürün Kararları & Mimari Tercihler

- **Karar 1: Video Akış Motoru Katmanı**
  - *Seçim:* ExoPlayer RTSP (TCP Interleaved) + Snapshot Polling yedek modu.
  - *Neden:* UDP paket kaybı araç içi Wi-Fi parazitlerinde siyah ekrana neden olur; TCP tünelleme ve anlık HTTP snapshot yedeği sıfır siyah ekran garantisi sunar.
- **Karar 2: İvmelenme Ölçüm Hassasiyeti**
  - *Seçim:* Yüksek frekanslı GPS (1Hz-10Hz) ve OBD-II PID 0D (Vehicle Speed) hız verisi interpolasyonu.
  - *Neden:* Sadece GPS tünel ve ağaçlık alanlarda gecikirken, OBD verisi tekerlek hız sensöründen doğrudan milisaniyelik tepki verir.
- **Karar 3: Yerel Veri Saklama**
  - *Seçim:* Room Database / DataStore ile cihaz üzerinde yerel seyahat arşivi.
  - *Neden:* Sürüş verileri çevrimdışı çalışmalıdır; internet bağlantısı gerektirmeyen sıfır gecikmeli yerel depolama esastır.

---

## 4. Teknik Mimari & Veri Akışı

```
┌─────────────────────────────────────────────────────────────┐
│                   KULLANICI ARAYÜZÜ (COMPOSE)               │
│  ┌───────────────────────┐         ┌─────────────────────┐  │
│  │ WifiCameraStreamPlayer│         │ PerformanceHudView  │  │
│  │ (RTSP / Snapshots)    │         │ (0-100, RPM, Gauge) │  │
│  └───────────┬───────────┘         └──────────┬──────────┘  │
└──────────────┼────────────────────────────────┼─────────────┘
               │                                │
┌──────────────▼────────────────────────────────▼─────────────┐
│                 ObdDashboardViewModel                       │
│  - StreamHealthMonitor (Ping, Port 554/3333 Socket Watchdog)│
│  - AccelerationTimerManager (0-100 km/s, 400m Drag timer)   │
│  - TripRecorderManager (Trip Duration, Max Speed, Fuel)     │
└──────────────┬────────────────────────────────┬─────────────┘
               │                                │
┌──────────────▼──────────┐          ┌──────────▼─────────────┐
│  Hardware Network Bind  │          │  Local Persistence     │
│  - WifiNetworkManager   │          │  - TripDatabase (Room) │
│  - Port 3333 Keepalive  │          │  - User Preferences    │
└─────────────────────────┘          └────────────────────────┘
```

### Planlanan Uygulama Adımları:
1. **Adım 1:** `WifiCameraStreamPlayer.kt` ve `XiaomiCameraCommander.kt` içerisine anlık soket doğrulama testi (Hardware Connectivity Bar) ve otomatik düşük gecikmeli RTSP tamponlama ayarlarının eklenmesi.
2. **Adım 2:** `ObdDashboardViewModel.kt` ve `CameraOverlayScreen.kt` içerisine 0-100 km/s performans sayacı, vites değişim ışıkları (Shift Light bar) ve Yarış (Race HUD) temasının entegrasyonu.
3. **Adım 3:** Seyahat özeti (Trip Logbook) ekranı ve seyahat bitiminde otomatik kayıt/dışa aktarma altyapısının hazırlanması.
4. **Adım 4:** Projenin derlenmesi ve doğrulama testlerinin yapılması.
