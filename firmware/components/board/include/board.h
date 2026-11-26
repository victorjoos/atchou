#pragma once

#include <stdbool.h>

#include "esp_err.h"

// Waveshare ESP32-S3-PhotoPainter (Rev 2.1)
#define BOARD_KEY_GPIO 4
#define BOARD_BOOT_GPIO 0
#define BOARD_RTC_INT_GPIO 6

#ifdef __cplusplus
extern "C" {
#endif

esp_err_t board_init(void);

// -1 when no battery is detected
int board_battery_percent(void);
int board_battery_mv(void);
bool board_usb_connected(void);

void board_led_activity(bool on);

// Quiet the peripherals and hand over to the PMIC sleep mode.
void board_prepare_for_sleep(void);

#ifdef __cplusplus
}
#endif
