---

### How to Save this to Your Repo in iSH

Run this quote-safe command in your iSH terminal to write the `README.md` file directly into your repository:

```bash
cat << 'EOF' > README.md
# 🚀 Tiny Launcher (Android TV)

> **Ultra-Lightweight, Pure Java 17 Android TV Launcher**  
> Designed for Android 14 (API 34) & 4K UHD Displays | Zero External Dependencies | Single-Activity Architecture

---

## 🌟 Key Highlights & Performance

* **Pure Java 17 / Zero Bloat:** Built completely within a single-activity architecture (`LauncherActivity.java`) without heavy AndroidX Leanback dependencies or third-party background thread pools.
* **Dual Resolution Engine (4K UHD ↔ 2K FHD):** Switch dynamically between **Native 4K** ($3840 \times 2160$) and **2K** ($1920 \times 1080$) in true 32-bit `ARGB_8888` color depth.
* **Ultra-Low Memory Footprint:** Consumes as little as **`~44 MB - 58 MB RAM`** in 2K mode, leaving over $97\%$ of device memory free for memory-heavy 4K streaming apps (Netflix, YouTube, Kodi, Plex).
* **Flat 0.0% Idle CPU:** Zero background polling leaks, cached clock formatters, and suspended background handlers whenever third-party apps run.

---

## 🖼️ Wallpaper & Slideshow Engine

### 1. Dynamic Wallpaper Resolution (`4k ARGB8888` ↔ `2k ARGB8888`)
Tiny Launcher features a real-time resolution switcher located directly inside the **Wallpaper/slideshow** submenu:

* **`4k ARGB8888` (Native 4K UHD — $3840 \times 2160$):**
  * Decodes wallpapers at exact 1:1 pixel mapping.
  * Preserves razor-sharp detail on 55"+ 4K TVs with full 32-bit color depth (no color banding).
  * Uses $\approx 160 - 235\text{ MB}$ RAM (dedicated 4K framebuffers).
* **`2k ARGB8888` (Full HD — $1920 \times 1080$):**
  * Downsamples images to 1080p while **strictly retaining 32-bit `ARGB_8888` color depth**.
  * **Why switch to 2K?** Reduces total launcher RAM down to **$\approx 44\text{ MB}$** on lower-spec hardware (1GB/2GB TV boxes) while completely avoiding the harsh color banding of 16-bit compression.
* **Live Refresh:** Switching resolutions using Remote Left / Right immediately re-decodes the active wallpaper on screen without requiring a device reboot.

### 2. Slideshow Intervals & "Change Each Restart" (10-Skip Algorithm)
* **Rotation Timer:** Select from `15sec`, `30sec`, `1min`, `5min`, `10min`, `20min`, `30min`, or `Off`.
* **Change Each Restart:** When enabled, the launcher automatically **skips 10 wallpapers forward** (`(lastIndex + 10) % count`) on every reboot or TV power cycle. You are guaranteed a brand new wallpaper every time your TV turns on.
* **Cold-Boot Instant Startup:** Features a resilient storage-mount retry loop that recovers from slow external storage initialization within $\approx 200\text{ms}$ of boot.
* **Direct File System & SAF Support:** Load wallpapers directly from `/sdcard/Pictures/Wallpapers` or select custom folders via the System Folder Picker.

---

## 🌦️ Smart Weather Widget & Hybrid Sensor API

Tiny Launcher features a multi-tiered, hybrid weather engine that seamlessly merges cloud forecasts with physical smart home sensors:

* **No Configuration:** Widget stays blank (`--`) with zero background network requests.
* **Location Only:** Powered by Open-Meteo (completely free, worldwide, no API keys required). Search any city or town directly on your TV.
* **Shelly Cloud Sensor Integration:** Enter your Shelly API URL to display real-time, physical room temperature and relative humidity directly on your home screen.
* **Smart Combination:** When both Shelly and Location are configured, Shelly provides physical **Temperature & Humidity**, while Open-Meteo provides the **Weather Icon, Condition Text, Wind Speed, and Wind Gusts**.
* **Zero-Drain Background Protection:** When the weather widget is turned **Off** in settings, the background polling thread (`ScheduledExecutorService`) is **instantly shut down**, stopping all network activity completely.

### iPhone / Phone Web Setup (QR Code & Local HTTP Server)
Avoid typing long API URLs and complex keys using a TV remote:

1. Open **Side Drawer $\rightarrow$ Weather menu $\rightarrow$ Web Setup (iPhone)**.
2. A QR code and local IP link (`http://<tv-ip>:8080`) are displayed on your TV.
3. Scan the QR code with your iPhone / phone camera to open a local setup webpage in Safari.
4. Paste your Shelly Cloud URL or Open-Meteo API key on your phone and tap **Save**.
5. The TV updates its weather configuration live, closes the server, and shuts down port 8080 immediately.

---

## 🎨 Tile Menu & Customization

Fine-tune every visual dimension of your Home Screen app row in real time using the Side Drawer:

* **Tile Corner Radius (`◠`):** `0°` (sharp square) to `90°` (fully rounded capsule ends) in $5^\circ$ steps.
* **Tile Size (`◫`):** `100dp` to `200dp` in 16:9 aspect ratio ($H = W \times 9 / 16$).
* **Row Vertical Position (`↕`):** `-50dp` to `+50dp` in 10dp steps.
* **Text Size (`Aa`):** `10sp` to `20sp` for focused app labels.
* **Text Position (`⇕`):** `-150dp` to `0dp` vertical label translation.
* **Tile Background Colour (`◩`):**
  * **`Off`:** Dynamic color shifting synced to wallpaper dominant colors with a synchronized left-to-right color sweep.
  * **10 Dark Muted Presets:** Lock tile backgrounds to elegant tones: `Red`, `Green`, `Blue`, `Yellow`, `Purple`, `Dark`, `Brown`, `Cyan`, or `Pink`.

---

## 🎮 TV Navigation & Focus Shields

* **Universal HOME Button Reset:** Pressing **HOME** from any submenu or popup immediately dismisses all overlays, scrolls to the far left, and places focus on **Tile 0**.
* **Idle UI Key Shield:** Wakes the screen on any button press **without accidentally launching apps**.
* **D-Pad Glide Move Mode (`↔`):** Long-press any app tile $\rightarrow$ select **Move App** $\rightarrow$ glide tiles with D-pad Left/Right arrows $\rightarrow$ press OK to save.

---

## 🛠️ Setting Tiny Launcher as Default Home

```bash
adb shell cmd package set-home-activity com.tiny.launcher/.LauncherActivity
adb shell input keyevent HOME
