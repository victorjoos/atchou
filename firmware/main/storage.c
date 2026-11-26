#include "storage.h"

#include <string.h>

#include "nvs.h"
#include "nvs_flash.h"

#define NS "atchou"

esp_err_t storage_init(void)
{
    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    return ret;
}

static bool get_str(nvs_handle_t h, const char *key, char *out, size_t cap)
{
    size_t len = cap;
    out[0] = '\0';
    return nvs_get_str(h, key, out, &len) == ESP_OK;
}

bool storage_load_config(frame_config_t *cfg)
{
    nvs_handle_t h;
    memset(cfg, 0, sizeof(*cfg));
    if (nvs_open(NS, NVS_READONLY, &h) != ESP_OK) {
        return false;
    }
    get_str(h, "ssid", cfg->ssid, sizeof cfg->ssid);
    get_str(h, "pass", cfg->pass, sizeof cfg->pass);
    get_str(h, "url", cfg->url, sizeof cfg->url);
    get_str(h, "token", cfg->token, sizeof cfg->token);
    nvs_close(h);
    return cfg->ssid[0] && cfg->url[0] && cfg->token[0];
}

esp_err_t storage_save_config(const frame_config_t *cfg)
{
    nvs_handle_t h;
    esp_err_t ret = nvs_open(NS, NVS_READWRITE, &h);
    if (ret != ESP_OK) {
        return ret;
    }
    ret = nvs_set_str(h, "ssid", cfg->ssid);
    if (ret == ESP_OK) ret = nvs_set_str(h, "pass", cfg->pass);
    if (ret == ESP_OK) ret = nvs_set_str(h, "url", cfg->url);
    if (ret == ESP_OK) ret = nvs_set_str(h, "token", cfg->token);
    if (ret == ESP_OK) {
        nvs_erase_key(h, "etag");  // new settings: force a fresh download
        ret = nvs_commit(h);
    }
    nvs_close(h);
    return ret;
}

esp_err_t storage_forget_wifi(void)
{
    nvs_handle_t h;
    esp_err_t ret = nvs_open(NS, NVS_READWRITE, &h);
    if (ret != ESP_OK) {
        return ret;
    }
    nvs_erase_key(h, "ssid");
    nvs_erase_key(h, "pass");
    ret = nvs_commit(h);
    nvs_close(h);
    return ret;
}

bool storage_get_etag(char *out, size_t cap)
{
    nvs_handle_t h;
    if (nvs_open(NS, NVS_READONLY, &h) != ESP_OK) {
        out[0] = '\0';
        return false;
    }
    bool ok = get_str(h, "etag", out, cap);
    nvs_close(h);
    return ok && out[0];
}

static void set_u8(const char *key, uint8_t v)
{
    nvs_handle_t h;
    if (nvs_open(NS, NVS_READWRITE, &h) == ESP_OK) {
        nvs_set_u8(h, key, v);
        nvs_commit(h);
        nvs_close(h);
    }
}

static uint8_t get_u8(const char *key)
{
    nvs_handle_t h;
    uint8_t v = 0;
    if (nvs_open(NS, NVS_READONLY, &h) == ESP_OK) {
        nvs_get_u8(h, key, &v);
        nvs_close(h);
    }
    return v;
}

void storage_set_etag(const char *etag)
{
    nvs_handle_t h;
    if (nvs_open(NS, NVS_READWRITE, &h) == ESP_OK) {
        if (etag && etag[0]) {
            nvs_set_str(h, "etag", etag);
        } else {
            nvs_erase_key(h, "etag");
        }
        nvs_commit(h);
        nvs_close(h);
    }
}

unsigned storage_get_failures(void) { return get_u8("fails"); }
void storage_set_failures(unsigned n) { set_u8("fails", n > 255 ? 255 : (uint8_t)n); }
bool storage_get_auth_failed(void) { return get_u8("authfail") != 0; }
void storage_set_auth_failed(bool failed) { set_u8("authfail", failed ? 1 : 0); }
