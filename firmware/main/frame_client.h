#pragma once

#include <stddef.h>
#include <stdint.h>

#include "esp_err.h"
#include "storage.h"

#define PANEL_BUFFER_SIZE (800 * 480 / 2)

typedef struct {
    int status;  // HTTP status: 200, 204, 304, 401, ...
    char etag[96];
    char next_wake[24];
    char server_time[24];
} frame_result_t;

// Asks the server for the current image. `etag` may be NULL/empty to force a download.
// On a 200 the packed panel image is written to `buf` (PANEL_BUFFER_SIZE bytes).
esp_err_t frame_fetch(const frame_config_t *cfg, int battery_pct, int battery_mv, const char *etag,
                      uint8_t *buf, frame_result_t *out);
