#include "screens.h"

#include <stdio.h>
#include <string.h>

#include "epaper.h"
#include "esp_log.h"
#include "font8x8_basic.h"
#include "font8x8_ext_latin.h"
#include "qrcode.h"

#define W 800
#define H 480


static void put(uint8_t *buf, int x, int y, uint8_t color)
{
    if (x < 0 || x >= W || y < 0 || y >= H) {
        return;
    }
    uint8_t *p = &buf[(y * W + x) / 2];
    *p = (x & 1) ? (uint8_t)((*p & 0xF0) | color) : (uint8_t)((*p & 0x0F) | (color << 4));
}

static void rect(uint8_t *buf, int x, int y, int w, int h, uint8_t color)
{
    for (int j = 0; j < h; j++) {
        for (int i = 0; i < w; i++) {
            put(buf, x + i, y + j, color);
        }
    }
}

static const unsigned char *glyph(uint32_t cp)
{
    if (cp < 128) {
        return font8x8_basic[cp];
    }
    if (cp >= 0xA0 && cp <= 0xFF) {
        return font8x8_ext_latin[cp - 0xA0];
    }
    return font8x8_basic['?'];
}

// Draws UTF-8 text; returns the x position after the last glyph.
static int text(uint8_t *buf, int x, int y, int scale, uint8_t color, const char *s)
{
    while (*s) {
        uint32_t cp = (unsigned char)*s++;
        if (cp >= 0xC0 && *s) {  // two-byte sequence (Latin-1 range)
            cp = ((cp & 0x1F) << 6) | ((unsigned char)*s++ & 0x3F);
        }
        const unsigned char *g = glyph(cp);
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (g[row] & (1 << col)) {
                    rect(buf, x + col * scale, y + row * scale, scale, scale, color);
                }
            }
        }
        x += 8 * scale;
    }
    return x;
}

static struct {
    uint8_t *buf;
    int x, y, size;
} s_qr;

static void qr_draw(esp_qrcode_handle_t qr)
{
    int n = esp_qrcode_get_size(qr);
    int scale = s_qr.size / (n + 8);
    int total = (n + 8) * scale;
    rect(s_qr.buf, s_qr.x, s_qr.y, total, total, EPD_7IN3E_WHITE);  // quiet zone
    for (int y = 0; y < n; y++) {
        for (int x = 0; x < n; x++) {
            if (esp_qrcode_get_module(qr, x, y)) {
                rect(s_qr.buf, s_qr.x + (x + 4) * scale, s_qr.y + (y + 4) * scale, scale, scale,
                     EPD_7IN3E_BLACK);
            }
        }
    }
}

// Characters with a special meaning in the WIFI: URI scheme must be escaped.
static void wifi_escape(const char *in, char *out, size_t cap)
{
    size_t n = 0;
    for (; *in && n + 2 < cap; in++) {
        if (strchr("\\;,:\"", *in)) {
            out[n++] = '\\';
        }
        out[n++] = *in;
    }
    out[n] = '\0';
}

void screen_render_setup(uint8_t *buf, const char *ap_ssid, const char *ap_pass)
{
    memset(buf, (EPD_7IN3E_WHITE << 4) | EPD_7IN3E_WHITE, (W * H / 2));
    rect(buf, 0, 0, W, 56, EPD_7IN3E_BLUE);
    text(buf, 24, 12, 4, EPD_7IN3E_WHITE, "Configuration du cadre");

    char line[64];
    text(buf, 24, 84, 2, EPD_7IN3E_BLACK, "1. Rejoignez ce réseau Wi-Fi :");
    snprintf(line, sizeof line, "Nom  : %s", ap_ssid);
    text(buf, 24, 124, 3, EPD_7IN3E_BLACK, line);
    snprintf(line, sizeof line, "Code : %s", ap_pass);
    text(buf, 24, 164, 3, EPD_7IN3E_BLACK, line);

    text(buf, 24, 236, 2, EPD_7IN3E_BLACK, "2. Ouvrez cette adresse :");
    text(buf, 24, 272, 3, EPD_7IN3E_RED, "http://192.168.4.1");
    text(buf, 24, 316, 2, EPD_7IN3E_BLACK, "(la page s'ouvre souvent seule)");
    text(buf, 24, 380, 2, EPD_7IN3E_BLACK, "Scannez le code pour rejoindre");
    text(buf, 24, 404, 2, EPD_7IN3E_BLACK, "le réseau sans rien taper.");

    char e_ssid[80], e_pass[48], payload[192];
    wifi_escape(ap_ssid, e_ssid, sizeof e_ssid);
    wifi_escape(ap_pass, e_pass, sizeof e_pass);
    snprintf(payload, sizeof payload, "WIFI:T:WPA;S:%s;P:%s;;", e_ssid, e_pass);

    s_qr.buf = buf;
    s_qr.x = 520;
    s_qr.y = 90;
    s_qr.size = 260;
    esp_qrcode_config_t cfg = ESP_QRCODE_CONFIG_DEFAULT();
    cfg.display_func = qr_draw;
    cfg.max_qrcode_version = 6;
    cfg.qrcode_ecc_level = ESP_QRCODE_ECC_MED;
    esp_qrcode_generate(&cfg, payload);
}

void screen_render_message(uint8_t *buf, const char *title, const char *l1, const char *l2,
                           const char *l3)
{
    memset(buf, (EPD_7IN3E_WHITE << 4) | EPD_7IN3E_WHITE, (W * H / 2));
    rect(buf, 0, 0, W, 56, EPD_7IN3E_RED);
    text(buf, 24, 12, 4, EPD_7IN3E_WHITE, title);
    const char *lines[3] = {l1, l2, l3};
    for (int i = 0; i < 3; i++) {
        if (lines[i]) {
            text(buf, 24, 100 + i * 48, 3, EPD_7IN3E_BLACK, lines[i]);
        }
    }
}
