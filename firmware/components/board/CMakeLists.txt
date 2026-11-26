idf_component_register(
    SRCS "board.c"
    INCLUDE_DIRS "include"
    REQUIRES axp2101 pcf85063 epaper driver esp_driver_gpio esp_driver_i2c esp_driver_spi esp_rom
)
