# Firmware du cadre (ESP32-S3 PhotoPainter, Rev 2.1)

Minimal ESP-IDF 6 app. Each wake: Wi-Fi → NTP → `GET /frame/image` (Bearer token, ETag) →
refresh the panel only on `200` → RTC alarm from `X-Next-Wake-At` → deep sleep.

## Build and flash

    . ~/esp/esp-idf/export.sh        # ESP-IDF v6.0.x
    cd firmware
    idf.py set-target esp32s3        # first time only
    idf.py build flash monitor

Host tests of the pure logic (sleep planning, form decoding):

    gcc -Wall -Wextra -o /tmp/test_logic host_tests/test_logic.c main/sleep_plan.c main/form.c && /tmp/test_logic

## Behaviour

- First boot (or no stored settings): the frame opens a Wi-Fi access point and shows its name,
  a random password, the address and a join QR code on the panel. The form asks for the Wi-Fi,
  the server URL and the device token.
- KEY short press: refresh now (ignores the ETag). KEY held 5 s: forget the Wi-Fi and reopen setup.
- Failures: retry after 15 min, then 1 h, then `ATCHOU_FALLBACK_SLEEP_SECONDS` (default 6 h).
- HTTP 401: shows an error screen once, then sleeps at the fallback interval.
- Only `https://` is accepted unless `ATCHOU_ALLOW_PLAIN_HTTP` is set (`idf.py menuconfig`).

## To verify on the hardware

- The PCF85063 alarm wakes the chip from deep sleep (GPIO6, ext1); the timer wake is a backup.
- `AXP2101_DISABLE_RAILS_ON_SLEEP` (off by default): on some revisions cutting ALDO3 locks the
  I²C bus. Enabling it saves power; check that wake-ups still work on your board.
- Real sleep current (the "months of battery" goal).

## Credits

Drivers derive from [aitjcize/esp32-photoframe](https://github.com/aitjcize/esp32-photoframe)
(MIT, see `LICENSE.aitjcize`), which embeds XPowersLib (MIT). The 8×8 font is public domain
([dhepper/font8x8](https://github.com/dhepper/font8x8)).
