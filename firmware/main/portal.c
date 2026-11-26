#include "portal.h"

#include <stdio.h>
#include <string.h>

#include "dns_server.h"
#include "epaper.h"
#include "esp_event.h"
#include "esp_http_server.h"
#include "esp_log.h"
#include "esp_mac.h"
#include "esp_netif.h"
#include "esp_random.h"
#include "esp_system.h"
#include "esp_wifi.h"
#include "form.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "screens.h"
#include "sdkconfig.h"
#include "storage.h"

static const char *TAG = "portal";

#define MAX_SCAN 20

static char s_options[MAX_SCAN * 48 + 8];

static void build_scan_options(void)
{
    s_options[0] = '\0';
    wifi_scan_config_t sc = {.show_hidden = false};
    if (esp_wifi_scan_start(&sc, true) != ESP_OK) {
        return;
    }
    uint16_t n = MAX_SCAN;
    wifi_ap_record_t recs[MAX_SCAN];
    if (esp_wifi_scan_get_ap_records(&n, recs) != ESP_OK) {
        return;
    }
    size_t len = 0;
    for (int i = 0; i < n; i++) {
        const char *ssid = (const char *)recs[i].ssid;
        // Skip names that would need HTML escaping
        if (!ssid[0] || strpbrk(ssid, "<>&\"'")) {
            continue;
        }
        len += snprintf(s_options + len, sizeof s_options - len, "<option value=\"%s\">", ssid);
        if (len >= sizeof s_options - 48) {
            break;
        }
    }
}

static esp_err_t form_handler(httpd_req_t *req)
{
    static char page[2600];
    snprintf(page, sizeof page,
             "<!doctype html><html lang=fr><head><meta charset=utf-8>"
             "<meta name=viewport content='width=device-width,initial-scale=1'>"
             "<title>Cadre photo</title><style>body{font-family:sans-serif;max-width:28em;"
             "margin:1em auto;padding:0 1em}label{display:block;margin-top:1em;font-weight:bold}"
             "input{width:100%%;padding:.6em;font-size:1em;box-sizing:border-box}"
             "button{margin-top:1.5em;padding:.8em;width:100%%;font-size:1.1em}</style></head>"
             "<body><h1>Configuration du cadre</h1><form method=post action=/save>"
             "<label>Réseau Wi-Fi</label><input name=ssid list=nets required maxlength=32>"
             "<datalist id=nets>%s</datalist>"
             "<label>Mot de passe Wi-Fi</label><input name=pass type=password maxlength=64>"
             "<label>Adresse du serveur</label><input name=url value='%s' required maxlength=159>"
             "<label>Jeton de l'appareil</label><input name=token required maxlength=95>"
             "<button>Enregistrer</button></form></body></html>",
             s_options, CONFIG_ATCHOU_DEFAULT_SERVER_URL);
    httpd_resp_set_type(req, "text/html; charset=utf-8");
    return httpd_resp_send(req, page, HTTPD_RESP_USE_STRLEN);
}

static void restart_task(void *arg)
{
    vTaskDelay(pdMS_TO_TICKS(1500));
    esp_restart();
}

static esp_err_t save_handler(httpd_req_t *req)
{
    char body[512];
    if (req->content_len == 0 || req->content_len >= sizeof body) {
        return httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "Formulaire trop grand");
    }
    int got = 0;
    while (got < (int)req->content_len) {
        int n = httpd_req_recv(req, body + got, req->content_len - got);
        if (n <= 0) {
            return ESP_FAIL;
        }
        got += n;
    }
    body[got] = '\0';

    frame_config_t cfg = {0};
    if (!form_get(body, "ssid", cfg.ssid, sizeof cfg.ssid) || !cfg.ssid[0] ||
        !form_get(body, "pass", cfg.pass, sizeof cfg.pass) ||
        !form_get(body, "url", cfg.url, sizeof cfg.url) || !cfg.url[0] ||
        !form_get(body, "token", cfg.token, sizeof cfg.token) || !cfg.token[0]) {
        return httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "Champs manquants ou trop longs");
    }
    if (storage_save_config(&cfg) != ESP_OK) {
        return httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "Échec de l'enregistrement");
    }
    httpd_resp_set_type(req, "text/html; charset=utf-8");
    httpd_resp_sendstr(req,
                       "<!doctype html><meta charset=utf-8><meta name=viewport "
                       "content='width=device-width,initial-scale=1'><body style='font-family:"
                       "sans-serif;margin:2em'><h1>C'est enregistré&nbsp;!</h1>"
                       "<p>Le cadre redémarre et affichera bientôt la première photo.</p>");
    xTaskCreate(restart_task, "restart", 2048, NULL, 5, NULL);
    return ESP_OK;
}

// Anything else (captive-portal probes) goes to the form.
static esp_err_t redirect_handler(httpd_req_t *req, httpd_err_code_t err)
{
    httpd_resp_set_status(req, "302 Found");
    httpd_resp_set_hdr(req, "Location", "http://192.168.4.1/");
    return httpd_resp_send(req, NULL, 0);
}

void portal_run(uint8_t *panel_buf)
{
    // Unambiguous characters only: the code is typed by hand if the QR isn't used.
    static const char alphabet[] = "abcdefghjkmnpqrstuvwxyz23456789";
    char pass[9];
    for (int i = 0; i < 8; i++) {
        pass[i] = alphabet[esp_random() % (sizeof alphabet - 1)];
    }
    pass[8] = '\0';

    uint8_t mac[6];
    esp_read_mac(mac, ESP_MAC_WIFI_SOFTAP);
    char ssid[24];
    snprintf(ssid, sizeof ssid, "Cadre-%02X%02X", mac[4], mac[5]);

    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    esp_netif_create_default_wifi_ap();
    esp_netif_create_default_wifi_sta();
    wifi_init_config_t init = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&init));
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_APSTA));
    ESP_ERROR_CHECK(esp_wifi_start());
    build_scan_options();

    wifi_config_t ap = {0};
    strlcpy((char *)ap.ap.ssid, ssid, sizeof ap.ap.ssid);
    strlcpy((char *)ap.ap.password, pass, sizeof ap.ap.password);
    ap.ap.ssid_len = strlen(ssid);
    ap.ap.channel = 1;
    ap.ap.max_connection = 2;
    ap.ap.authmode = WIFI_AUTH_WPA2_PSK;
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_AP, &ap));

    httpd_handle_t server = NULL;
    httpd_config_t hc = HTTPD_DEFAULT_CONFIG();
    hc.max_uri_handlers = 4;
    hc.stack_size = 6144;
    hc.lru_purge_enable = true;
    ESP_ERROR_CHECK(httpd_start(&server, &hc));
    httpd_uri_t root = {.uri = "/", .method = HTTP_GET, .handler = form_handler};
    httpd_uri_t save = {.uri = "/save", .method = HTTP_POST, .handler = save_handler};
    httpd_register_uri_handler(server, &root);
    httpd_register_uri_handler(server, &save);
    httpd_register_err_handler(server, HTTPD_404_NOT_FOUND, redirect_handler);
    dns_server_start();
    ESP_LOGI(TAG, "Portal up: SSID %s", ssid);

    // The panel refresh takes ~25 s; the portal is already usable meanwhile.
    screen_render_setup(panel_buf, ssid, pass);
    epaper_display(panel_buf);
    epaper_enter_deepsleep();

    vTaskDelay(pdMS_TO_TICKS(CONFIG_ATCHOU_PORTAL_TIMEOUT_SECONDS * 1000));
    dns_server_stop();
    httpd_stop(server);
    esp_wifi_stop();
    ESP_LOGW(TAG, "Portal timed out");
}
