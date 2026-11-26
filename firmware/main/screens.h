#pragma once

#include <stdint.h>

// Draw the setup screen (Wi-Fi name, password, address and a join QR code) into `buf`.
void screen_render_setup(uint8_t *buf, const char *ap_ssid, const char *ap_pass);

// Draw a full-screen French message (title + up to 3 body lines) into `buf`.
void screen_render_message(uint8_t *buf, const char *title, const char *l1, const char *l2,
                           const char *l3);
