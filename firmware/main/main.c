#include <string.h>
#include <sys/time.h>
#include <time.h>

#include "board.h"
#include "driver/gpio.h"
#include "driver/rtc_io.h"
#include "epaper.h"
#include "esp_heap_caps.h"
#include "esp_log.h"
#include "esp_sleep.h"
#include "esp_timer.h"
#include "frame_client.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "pcf85063.h"
#include "portal.h"
#include "screens.h"
#include "sdkconfig.h"
#include "sleep_plan.h"
#include "storage.h"
#include "wifi_sta.h"

static const char *TAG = "atchou";

#define PLAUSIBLE_TIME 1767225600  // 2026-01-01: anything earlier is an unset clock
#define LONG_PRESS_MS 5000
#define WAKE_GRACE_S 120

static bool clock_is_set(void)
{
    return time(NULL) >= PLAUSIBLE_TIME;
}

static void load_clock_from_rtc(void)
{
    time_t t;
    if (pcf85063_read_time(&t) == ESP_OK && t >= PLAUSIBLE_TIME) {
        struct timeval tv = {.tv_sec = t};
        settimeofday(&tv, NULL);
    }
}

static bool key_woke_us(void)
{
    if (!(esp_sleep_get_wakeup_causes() & BIT(ESP_SLEEP_WAKEUP_EXT1))) {
        return false;
    }
    return (esp_sleep_get_ext1_wakeup_status() & (1ULL << BOARD_KEY_GPIO)) != 0;
}

static bool key_held_long(void)
{
    board_led_activity(true);
    int64_t start = esp_timer_get_time();
    while (gpio_get_level(BOARD_KEY_GPIO) == 0) {
        if ((esp_timer_get_time() - start) / 1000 >= LONG_PRESS_MS) {
            return true;
        }
        vTaskDelay(pdMS_TO_TICKS(50));
    }
    board_led_activity(false);
    return false;
}

static void enter_deep_sleep(uint32_t seconds)
{
    time_t now = time(NULL);
    pcf85063_clear_alarm();
    if (clock_is_set()) {
        // Alarm on the RTC INT line wakes us even if the ESP timer drifts.
        pcf85063_set_alarm(now + seconds);
    }

    uint64_t mask = (1ULL << BOARD_KEY_GPIO) | (1ULL << BOARD_RTC_INT_GPIO);
    for (int pin = 0; pin < 64; pin++) {
        if (mask & (1ULL << pin)) {
            rtc_gpio_init(pin);
            rtc_gpio_set_direction(pin, RTC_GPIO_MODE_INPUT_ONLY);
            rtc_gpio_pullup_en(pin);
            rtc_gpio_pulldown_dis(pin);
        }
    }
    esp_sleep_enable_ext1_wakeup_io(mask, ESP_EXT1_WAKEUP_ANY_LOW);
    // Backup wake-up if the RTC alarm does not fire.
    esp_sleep_enable_timer_wakeup((uint64_t)(seconds + WAKE_GRACE_S) * 1000000ULL);

    ESP_LOGI(TAG, "Deep sleep for %u s", (unsigned)seconds);
    board_prepare_for_sleep();
    esp_deep_sleep_start();
}

static uint32_t fail_and_plan(void)
{
    unsigned n = storage_get_failures() + 1;
    storage_set_failures(n);
    return sleep_failure_backoff(n, CONFIG_ATCHOU_FALLBACK_SLEEP_SECONDS);
}

void app_main(void)
{
    bool key_wake = key_woke_us();

    ESP_ERROR_CHECK(storage_init());
    board_init();
    pcf85063_clear_alarm();
    load_clock_from_rtc();

    uint8_t *panel = heap_caps_malloc(PANEL_BUFFER_SIZE, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (!panel) {
        ESP_LOGE(TAG, "No PSRAM for the panel buffer");
        enter_deep_sleep(CONFIG_ATCHOU_FALLBACK_SLEEP_SECONDS);
    }

    if (key_wake && key_held_long()) {
        ESP_LOGW(TAG, "Long press: forgetting Wi-Fi");
        storage_forget_wifi();
        storage_set_etag(NULL);
    }
    board_led_activity(false);

    frame_config_t cfg;
    bool has_wifi = storage_load_config(&cfg);
    if (!cfg.token[0]) {
        ESP_LOGE(TAG, "No device token compiled in");
        screen_render_message(panel, "Jeton absent", "Ce cadre a été flashé sans jeton.",
                              "Reflashez-le avec le jeton", "de l'appareil.");
        epaper_display(panel);
        epaper_enter_deepsleep();
        enter_deep_sleep(CONFIG_ATCHOU_FALLBACK_SLEEP_SECONDS);
    }
    if (!has_wifi) {
        portal_run(panel);  // reboots after saving; returns only on timeout
        enter_deep_sleep(CONFIG_ATCHOU_FALLBACK_SLEEP_SECONDS);
    }

    int pct = board_battery_percent();
    int mv = board_battery_mv();
    ESP_LOGI(TAG, "Battery %d%% (%d mV)%s", pct, mv, board_usb_connected() ? ", USB" : "");

    uint32_t sleep_s = 0;
    bool wifi_ok = wifi_sta_connect(cfg.ssid, cfg.pass, 20000) == ESP_OK;
    if (!wifi_ok) {
        wifi_sta_stop();
        enter_deep_sleep(fail_and_plan());
    }

    if (wifi_sta_sync_time(4000) && clock_is_set()) {
        pcf85063_write_time(time(NULL));
    }

    char etag[96] = "";
    if (!key_wake) {
        storage_get_etag(etag, sizeof etag);  // a key press forces a redraw
    }

    frame_result_t res;
    esp_err_t err = frame_fetch(&cfg, pct, mv, etag, panel, &res);
    int64_t fetched_us = esp_timer_get_time();
    wifi_sta_stop();

    if (err != ESP_OK || res.status >= 500 || res.status == 0) {
        enter_deep_sleep(fail_and_plan());
    }

    if (res.status == 401) {
        if (!storage_get_auth_failed()) {
            screen_render_message(panel, "Accès refusé", "Le jeton de ce cadre est refusé",
                                  "par le serveur.", "Reflashez-le avec le bon jeton.");
            epaper_display(panel);
            epaper_enter_deepsleep();
            storage_set_auth_failed(true);
        }
        enter_deep_sleep(CONFIG_ATCHOU_FALLBACK_SLEEP_SECONDS);
    }

    if (res.status == 200) {
        epaper_display(panel);
        epaper_enter_deepsleep();
        storage_set_etag(res.etag);  // only once the image is really on the panel
    }
    if (res.status == 200 || res.status == 204 || res.status == 304) {
        storage_set_failures(0);
        storage_set_auth_failed(false);
    } else {
        enter_deep_sleep(fail_and_plan());
    }

    // Current UTC: the system clock if set, otherwise the server's clock plus elapsed time.
    time_t now = 0, wake = 0;
    bool have_wake = sleep_parse_utc(res.next_wake, &wake);
    if (clock_is_set()) {
        now = time(NULL);
    } else if (sleep_parse_utc(res.server_time, &now)) {
        now += (time_t)((esp_timer_get_time() - fetched_us) / 1000000);
        struct timeval tv = {.tv_sec = now};
        settimeofday(&tv, NULL);
        pcf85063_write_time(now);
    } else {
        have_wake = false;
    }
    sleep_s = have_wake ? sleep_seconds_until(now, wake) : CONFIG_ATCHOU_FALLBACK_SLEEP_SECONDS;
    enter_deep_sleep(sleep_s);
}
