idf_component_register(
    SRCS "main.c" "storage.c" "wifi_sta.c" "frame_client.c" "portal.c" "screens.c"
         "dns_server.c" "form.c" "sleep_plan.c"
    INCLUDE_DIRS "."
    REQUIRES board pcf85063 epaper esp_wifi esp_event esp_netif esp_http_client esp_http_server
             esp-tls nvs_flash esp_timer esp_driver_gpio esp_hw_support esp_psram mbedtls lwip
    PRIV_REQUIRES qrcode
)
