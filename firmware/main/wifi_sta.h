#pragma once

#include <stdbool.h>

#include "esp_err.h"

esp_err_t wifi_sta_connect(const char *ssid, const char *pass, int timeout_ms);
// Best-effort SNTP; true when the system clock was set from the network.
bool wifi_sta_sync_time(int timeout_ms);
void wifi_sta_stop(void);
