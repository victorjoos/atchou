#pragma once

#include <stdbool.h>
#include <stddef.h>

#include "esp_err.h"

typedef struct {
    char ssid[33];
    char pass[65];
    char url[160];
    char token[96];
} frame_config_t;

esp_err_t storage_init(void);
bool storage_load_config(frame_config_t *cfg);  // false if no Wi-Fi is stored
esp_err_t storage_save_wifi(const char *ssid, const char *pass);
esp_err_t storage_forget_wifi(void);

bool storage_get_etag(char *out, size_t cap);
void storage_set_etag(const char *etag);

unsigned storage_get_failures(void);
void storage_set_failures(unsigned n);

bool storage_get_auth_failed(void);
void storage_set_auth_failed(bool failed);
