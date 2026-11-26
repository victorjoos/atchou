# Firmware: candidate bases and libraries

_Research date: 2026-11-25. Star counts / licenses / push dates are from the GitHub API on that day.
Items marked **(unverified)** come from READMEs only; I did not build or run them._

## 1. Requirements used for the comparison

Source: `docs/requirements.md` ("Frame firmware") plus answers given for this research.

**Board:** Waveshare ESP32-S3-PhotoPainter (ESP32-S3-WROOM-1-N16R8, 7.3" 800×480 Spectra 6,
AXP2101 PMIC, PCF85063 RTC, SHTC3, SD slot, KEY/BOOT buttons).

**Must-have**
1. HTTPS with certificate validation (public CA; Caddy/Let's Encrypt on the VPS)
2. Device-token auth header (`/frame/image`)
3. Deep sleep until `next_wake_at` (server-provided absolute time, NTP-synced)
4. Fallback wake interval (~6 h) when the request fails
5. Physical button wake / refresh
6. RTC (PCF85063) wake
7. Wi-Fi provisioning through a **captive portal** (decided; BLE rejected as more complex)
8. Battery life measured in **months**
9. Open-source friendly **license**

**Nice-to-have:** low-battery screen.
**Not wanted:** OTA, SD cache, offline fallback, remote logging, device web UI, secure boot (not selected).

Server contract the firmware must speak (already implemented, `server/README.md`):
`GET /frame/image` with device token → packed 4-bit buffer (192 000 B = 800×480/2, nibbles
0=black 1=white 2=yellow 3=red 5=blue 6=green), `ETag`/`304`, battery report, `X-Next-Wake-At`.
Our server already emits the panel-native packed buffer, so **the firmware needs no image
decoding or dithering** (JPEG/PNG/BMP code in other projects is dead weight for us).

## 2. Hardware facts that affect the choice

- **Display driver:** SPI (DC 8, CS 9, SCK 10, MOSI 11, RST 12, BUSY 13); panel is
  Good Display GDEP073E01-class. Refresh ≈ 25–28 s, which is most of a wake's energy.
- **I²C bus** (SDA 47 / SCL 48) is shared by AXP2101 (0x34), PCF85063 (0x51), SHTC3, audio codecs.
  Rev 2.0 reports (EdenNelson `HARDWARE.md`): **ALDO3 must be enabled at boot and stay enabled or the
  whole I²C bus is clamped**. Panel power is ALDO4. Vendor deep sleep leaves rails on.
- **Wake sources:** KEY = GPIO4, BOOT = GPIO0 (both RTC-capable, ext1 wake); RTC interrupt = GPIO6;
  PMIC IRQ = GPIO21.
- **Sleep current:** Waveshare quotes ≤ 1 mA (≈ 1 mA at 5 V in their test); a user in
  waveshareteam/ESP32-S3-PhotoPainter#5 calls that high. At 1 mA a 1000–2000 mAh cell lasts
  ~6–12 weeks *before* refresh energy; at ~10 µA (claimed by aitjcize, **unverified**) it's years.
  **"Months" is feasible only if real sleep current is measured and acceptable on your unit** → do this early.
- **Board revisions differ** (rroels: v1 "major power issues"; EdenNelson: Rev 2.0 PMIC behaviour).
  Check which revision you own before trusting any of the above.

## 3. Candidate bases (whole projects)

| # | Project | Framework | License | Stars / last push | Fit |
|---|---------|-----------|---------|-------------------|-----|
| A | [aitjcize/esp32-photoframe](https://github.com/aitjcize/esp32-photoframe) | ESP-IDF (C) | MIT | 283 / 2026-11-25 | ★★★★ |
| B | [dmellok/tesserae-device-photopainter-7.3-bin](https://github.com/dmellok/tesserae-device-photopainter-7.3-bin) | ESP-IDF (C) | AGPL-3.0 | 3 / 2026-06 | ★★★ (license) |
| C | [OOHehir/esp32-photopainter](https://github.com/OOHehir/esp32-photopainter) | ESP-IDF 6.0.2 (C) | none stated (NOASSERTION) | 0 / 2026-11-14 | ★★★ (reference only) |
| D | [edwardtoday/photoframe](https://github.com/edwardtoday/photoframe) | Rust + ESP-IDF | none | 1 / 2026-07 | ★★ (reference only) |
| E | [waveshareteam/ESP32-S3-PhotoPainter](https://github.com/waveshareteam/ESP32-S3-PhotoPainter) | ESP-IDF 5.5.2 / 6.1 (xiaozhi-based) + Arduino demos | not detected by GitHub | 62 / 2026-11-04 | ★★ (HW reference) |
| F | [rroels/arduino_esp32_s3_photopainter](https://github.com/rroels/arduino_esp32_s3_photopainter) | Arduino-ESP32 | MIT | 0 / 2026-04 | ★★★ (Arduino starting point) |
| G | [EdenNelson/Waveshare-ESP32-S3-PhotoPainter](https://github.com/EdenNelson/Waveshare-ESP32-S3-PhotoPainter) | ESPHome (ESP-IDF) | GPL-3.0 | 3 / 2026-11-17 | ★★ (HW notes are gold) |

### A. aitjcize/esp32-photoframe — best existing base (ESP-IDF)
- Targets exactly this board (also Seeed XIAO / reTerminal / M5Paper) via a `board_hal`.
- Already has: captive-portal Wi-Fi provisioning (`wifi_provisioning.c` + `dns_server.c`), AXP2101 driver,
  **PCF85063 driver**, SHTC3, SD, deep sleep with ext1 button wake, **network backoff** (5 min → 6 h; matches our
  "fallback interval"), custom HTTP header for image fetches (usable as device token), certificate-bundle HTTPS
  (`esp_crt_bundle`), low-battery badge, OTA, crash log, mDNS, HA integration.
- Companion ecosystem: server, mobile app, HA integration — we'd ignore all of it.
- **Gaps vs. our needs:**
  - Scheduling is its own cron/interval "auto-rotate", **not** a server-supplied `next_wake_at`.
  - Image path expects JPEG/PNG/BMP and does its own palette-aware dithering; we'd add a raw 192 000-byte path
    (skip decoder) and wire `X-Next-Wake-At` + ETag.
  - Large surface (web UI, albums, HA, OTA…) we explicitly don't want → bigger flash, more to maintain, more
    awake time. Option: **fork and strip**, or **cherry-pick** the drivers/provisioning/backoff into a minimal app.
  - Verify its real sleep current and whether it handles the ALDO3/I²C clamp issue.

### B. Tesserae PhotoPainter client — closest *protocol* match
- Wake state machine, NVS schema, captive portal, SNTP, AXP2101 fuel gauge, `sleep_until` absolute timestamp in
  its heartbeat; REST mode polls a server. Same 192 000-byte nibble format and palette codes as ours
  (confirms `0=black 1=white 2=yellow 3=red 5=blue 6=green`).
- **AGPL-3.0** — copying code forces the whole firmware (and arguably network-facing parts) to AGPL. Acceptable only if
  you want AGPL. Otherwise use as a *design reference*, not a code source.

### C/D. OOHehir and edwardtoday — reference implementations
- C: ESP-IDF 6, very low-power oriented ("refresh only if picture changed", quiet hours, KEY wake, captive portal
  provisioning, signed OTA, offline cache). Clean component split (`app_cycle`, `app_epd`, `app_board`) is a good
  template. **No license** → read, don't copy.
- D: Rust on ESP-IDF; ETag/`If-None-Match`, token auth, SNTP, `SPURIOUS_EXT1` guard, backoff, AP portal.
  Chinese docs; no license. Useful for ideas (spurious-wake handling).

### E. Waveshare official repo
- Factory firmware = a modified **xiaozhi-esp32** (AI voice assistant). Heavy and not a fit as a base.
- Valuable as: pinout, panel init sequence, AXP2101 power sequencing, `04_PowerConsumptionTest`, Arduino
  `01_Audio_Test`. Licensing of the example code isn't stated by GitHub — check headers before copying.
- Docs: https://docs.waveshare.com/ESP32-S3-PhotoPainter

### F. rroels Arduino guide — best Arduino starting point
- Working snippets for every peripheral on this board with Arduino-ESP32: AXP2101 (XPowersLib), PCF85063
  (SensorLib), SHTC3, SD, PSRAM, buttons, and the display via **GxEPD2** (`GDEP073E01` / `ACeP730` classes).
- Not a firmware: no provisioning, deep-sleep loop, or networking logic.

### G. EdenNelson ESPHome package
- Not suitable for our server-poll/`next_wake_at` model (ESPHome expects HA/API), but **`HARDWARE.md` is the most
  rigorous hardware write-up**: rail map, AXP2101 registers, PPK2 current measurements, ext1 wake verification.
  Read before writing any PMIC code.

## 4. Libraries and building blocks

### ESP-IDF
| Need | Option | Notes |
|------|--------|-------|
| HTTPS + CA validation | `esp_http_client` + `esp_crt_bundle` (Mozilla bundle) | Built in; enough for Let's Encrypt. Use `If-None-Match` + custom headers for token/ETag |
| Time | `esp_sntp` + PCF85063 | Sync RTC from NTP when online; wake from RTC alarm |
| Captive portal | IDF `examples/protocols/http_server/captive_portal` (SoftAP + DNS catch-all + `esp_http_server`) | Official, MIT/Apache-style; aitjcize has a production variant |
| Alternative provisioning | `wifi_provisioning` (SoftAP/BLE, Espressif) | Needs Espressif's app/protocomm; not a web captive portal |
| Third-party portal | [tonyp7/esp32-wifi-manager](https://github.com/tonyp7/esp32-wifi-manager) (MIT, 786★, **last push 2024-06 — stale**) | Probably too old for IDF 5.5/6.x |
| NVS storage | `nvs_flash` | Token, SSID, server URL, last ETag |
| Display driver | aitjcize `components/epaper*` or Waveshare's `01_Example` driver | Write ~300 lines ourselves from the GDEP073E01 init sequence if keeping it minimal |
| PMIC | [lewisxhe/XPowersLib](https://github.com/lewisxhe/XPowersLib) (MIT, 139★, active) or aitjcize `pmic_driver_axp2101` | XPowersLib works with ESP-IDF as well |
| RTC | aitjcize `rtc_driver_pcf85063` or [lewisxhe/SensorLib](https://github.com/lewisxhe/SensorLib) (MIT, 257★) | Alarm on GPIO6 → ext0/ext1 wake |
| Deep sleep | `esp_sleep_enable_ext1_wakeup` (KEY/BOOT/RTC INT), `esp_sleep_enable_timer_wakeup` as backup | |
| Config | Kconfig + `sdkconfig.defaults` | |

### Arduino-ESP32
| Need | Option | Notes |
|------|--------|-------|
| Display | [ZinggJM/GxEPD2](https://github.com/ZinggJM/GxEPD2) v1.6.9 (GPL-3.0, 1.5k★) | Has `GDEP073E01` / `ACeP730`. **GPL-3.0** → firmware must be GPL-compatible. Author doesn't accept PRs/issues. Raw packed buffer may need low-level API use |
| PMIC | XPowersLib (MIT) | |
| RTC | SensorLib (MIT) / SolderedElectronics PCF85063A / teddokano RTC_NXP | |
| Wi-Fi + portal | [tzapu/WiFiManager](https://github.com/tzapu/WiFiManager) (MIT, 7.3k★, last push 2026-02) | Mature captive portal; adds RAM/flash and awake time |
| HTTPS | `WiFiClientSecure` + `setCACertBundle()` | Works, extra RAM |
| NTP | `configTime()` | |
| Build | Arduino IDE / arduino-cli / PlatformIO | Arduino-ESP32 3.x is built on ESP-IDF 5.x |
| Hybrid | Arduino as an ESP-IDF component | Lets you keep IDF drivers + Arduino libs; adds build complexity |

## 5. ESP-IDF vs Arduino for this project

| Criterion | ESP-IDF | Arduino-ESP32 |
|-----------|---------|---------------|
| Existing code for **this exact board** + provisioning + RTC + backoff | Yes (A, B, C) | Only snippets (F) |
| Deep-sleep power control, avoiding wasted awake time (months of battery) | Full control, no hidden init | OK but Arduino runtime adds boot/init cost |
| Wake-state handling (ext1, RTC, spurious wakes, `RTC_DATA_ATTR`) | First-class | Works, thinner docs |
| Display lib maturity | Roll or reuse small drivers | GxEPD2 (GPL-3.0) |
| Ease for contributors / open-source community | Steeper | Easier |
| Binary size, RAM | Smaller | Larger |
| License flexibility | Free choice | GPL if GxEPD2 used |

**Leaning:** ESP-IDF. The project already has a fully specified server contract and is single-purpose;
the only third-party ESP-IDF project with everything we need is permissively licensed (MIT).

## 6. Recommendation (to be confirmed)

1. **Primary:** start from **aitjcize/esp32-photoframe** (MIT).
   - Option 1 (preferred): *minimal new app* using its `board_hal`, `pmic_driver_axp2101`, `rtc_driver_pcf85063`,
     `epaper`, `wifi_provisioning`/`dns_server`, `network_backoff`, `cert_pin`/crt bundle as components; write our own
     `main` (wake → Wi-Fi → NTP → GET → draw → report → sleep until `next_wake_at`).
   - Option 2: fork and strip the web UI/albums/HA/OTA, add `next_wake_at` + raw-buffer path.
2. **Reference (read, don't copy):** EdenNelson `HARDWARE.md` for PMIC/I²C; OOHehir `app_cycle` for wake structure;
   Tesserae for protocol fields and `sleep_until`.
3. **Fallback if the stack proves too heavy:** Arduino + GxEPD2 + XPowersLib + SensorLib + WiFiManager, starting from rroels (F).
   Accept GPL-3.0.
4. **Before writing firmware:** measure deep-sleep current on the actual board (PPK2/multimeter) with stock firmware,
   with aitjcize firmware, and with PMIC rails off. This decides whether "months" is realistic.

## 7. Decisions

| Topic | Decision |
|-------|----------|
| Board revision | Rev 2.1 (re-check `HARDWARE.md`, written for Rev 2.0, against it; measure sleep current) |
| License | Open: GPL/AGPL code may be used. Unlocks Tesserae (AGPL) and GxEPD2 (GPL) as code sources. Base choice is unchanged (aitjcize, MIT) |
| Forking | Allowed |
| Time | NTP re-sync on every wake (Wi-Fi is already up) and write it to the PCF85063. The RTC backup cell keeps time through deep sleep and provides the alarm wake (needs a rechargeable cell) |
| Orientation | Landscape, no rotation |
| KEY button | Proposed below |
| ETag | Proposed below |

### Proposed KEY / BOOT behaviour
- **KEY short press (wake from deep sleep):** refresh now (fetch, and redraw only if the image changed).
- **KEY held ~5 s:** forget Wi-Fi and start the captive portal (SoftAP, instructions drawn on the panel).
- **BOOT:** left for flashing only (GPIO0 is a boot strap).
- **Spurious wakes** (button bounce, RTC INT glitch): if the wake reason is unclear, go back to sleep without Wi-Fi (idea from edwardtoday/photoframe).
- **Low battery:** small empty-battery badge below ~15 %. Below ~5 % draw a full "charge me" screen once, then sleep long.

### ETag persistence
The server sends an `ETag` for the current image. After deep sleep RAM is lost, so the firmware stores the last ETag
in NVS (or RTC memory) and sends `If-None-Match` on the next wake. If the server answers `304`, the firmware skips
the download and the ~25 s panel refresh, saving most of the wake's energy. Without storing it, every wake would
redraw even when nothing changed.

## 8. Update after starting the firmware

- Built on ESP-IDF 6 as a minimal app (`firmware/`), reusing aitjcize's MIT drivers.
- No RTC backup cell is needed: the PCF85063 runs from the main battery, and NTP fixes the time after a total power loss.
- The ETag is stored in NVS and only after a successful display.
- The setup screen is rendered by the firmware (8×8 public-domain font + QR code), with a random AP password.
- Low-battery handling is server-side (the frame sends `battery` and `battery_mv`): below 15 % a badge is drawn on the image, below 5 % a "charge me" screen is served with a 12 h wake interval (`server/src/atchou_server/battery.py`).
