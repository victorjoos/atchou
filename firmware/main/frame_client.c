#include "frame_client.h"

#include <stdio.h>
#include <string.h>

#include "esp_crt_bundle.h"
#include "esp_http_client.h"
#include "esp_log.h"

static const char *TAG = "frame_client";

static esp_err_t on_http_event(esp_http_client_event_t *evt)
{
    if (evt->event_id != HTTP_EVENT_ON_HEADER) {
        return ESP_OK;
    }
    frame_result_t *r = evt->user_data;
    if (!strcasecmp(evt->header_key, "ETag")) {
        strlcpy(r->etag, evt->header_value, sizeof r->etag);
    } else if (!strcasecmp(evt->header_key, "X-Next-Wake-At")) {
        strlcpy(r->next_wake, evt->header_value, sizeof r->next_wake);
    } else if (!strcasecmp(evt->header_key, "X-Server-Time")) {
        strlcpy(r->server_time, evt->header_value, sizeof r->server_time);
    }
    return ESP_OK;
}

esp_err_t frame_fetch(const frame_config_t *cfg, int battery_pct, int battery_mv, const char *etag,
                      uint8_t *buf, frame_result_t *out)
{
    memset(out, 0, sizeof(*out));

    bool is_https = strncmp(cfg->url, "https://", 8) == 0;
#ifndef CONFIG_ATCHOU_ALLOW_PLAIN_HTTP
    if (!is_https) {
        ESP_LOGE(TAG, "Refusing non-HTTPS server URL");
        return ESP_ERR_INVALID_ARG;
    }
#endif

    char url[256];
    size_t base_len = strlen(cfg->url);
    while (base_len > 0 && cfg->url[base_len - 1] == '/') {
        base_len--;
    }
    snprintf(url, sizeof url, "%.*s/frame/image?battery=%d&battery_mv=%d", (int)base_len, cfg->url,
             battery_pct, battery_mv);

    esp_http_client_config_t hc = {
        .url = url,
        .timeout_ms = 20000,
        .event_handler = on_http_event,
        .user_data = out,
        .buffer_size = 2048,
        .crt_bundle_attach = is_https ? esp_crt_bundle_attach : NULL,
    };
    esp_http_client_handle_t client = esp_http_client_init(&hc);
    if (!client) {
        return ESP_ERR_NO_MEM;
    }

    char auth[128];
    snprintf(auth, sizeof auth, "Bearer %s", cfg->token);
    esp_http_client_set_header(client, "Authorization", auth);
    if (etag && etag[0]) {
        esp_http_client_set_header(client, "If-None-Match", etag);
    }

    esp_err_t ret = esp_http_client_open(client, 0);
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "open failed: %s", esp_err_to_name(ret));
        esp_http_client_cleanup(client);
        return ret;
    }

    int64_t content_len = esp_http_client_fetch_headers(client);
    out->status = esp_http_client_get_status_code(client);
    ESP_LOGI(TAG, "HTTP %d, content-length %lld", out->status, (long long)content_len);

    if (out->status == 200) {
        if (content_len != PANEL_BUFFER_SIZE) {
            ESP_LOGE(TAG, "Unexpected image size %lld", (long long)content_len);
            ret = ESP_ERR_INVALID_SIZE;
        } else {
            size_t got = 0;
            while (got < PANEL_BUFFER_SIZE) {
                int n = esp_http_client_read(client, (char *)buf + got, PANEL_BUFFER_SIZE - got);
                if (n <= 0) {
                    break;
                }
                got += (size_t)n;
            }
            if (got != PANEL_BUFFER_SIZE) {
                ESP_LOGE(TAG, "Truncated image: %u bytes", (unsigned)got);
                ret = ESP_ERR_INVALID_SIZE;
            }
        }
    }

    esp_http_client_close(client);
    esp_http_client_cleanup(client);
    return ret;
}
