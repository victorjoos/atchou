#include "board.h"

#include "axp2101.h"
#include "driver/gpio.h"
#include "driver/i2c_master.h"
#include "driver/spi_master.h"
#include "epaper.h"
#include "esp_log.h"
#include "esp_rom_sys.h"
#include "pcf85063.h"

#define PIN_I2C_SDA 47
#define PIN_I2C_SCL 48
#define PIN_PMIC_IRQ 21
#define PIN_EPD_DC 8
#define PIN_EPD_CS 9
#define PIN_EPD_SCK 10
#define PIN_EPD_MOSI 11
#define PIN_EPD_RST 12
#define PIN_EPD_BUSY 13
#define PIN_LED_GREEN 42

// Below any usable LiPo yet far above the ~0 mV reported with no battery.
#define BATTERY_PRESENT_MIN_MV 2500

static const char *TAG = "board";
static i2c_master_bus_handle_t s_i2c;

// A slave can hold SDA low if the chip went to sleep mid-transaction.
static void i2c_bus_recover(void)
{
    gpio_set_direction(PIN_I2C_SCL, GPIO_MODE_OUTPUT_OD);
    gpio_set_direction(PIN_I2C_SDA, GPIO_MODE_OUTPUT_OD);
    gpio_set_pull_mode(PIN_I2C_SCL, GPIO_PULLUP_ONLY);
    gpio_set_pull_mode(PIN_I2C_SDA, GPIO_PULLUP_ONLY);
    for (int i = 0; i < 9; i++) {
        gpio_set_level(PIN_I2C_SCL, 0);
        esp_rom_delay_us(5);
        gpio_set_level(PIN_I2C_SCL, 1);
        esp_rom_delay_us(5);
    }
    gpio_set_level(PIN_I2C_SDA, 0);
    esp_rom_delay_us(5);
    gpio_set_level(PIN_I2C_SCL, 1);
    esp_rom_delay_us(5);
    gpio_set_level(PIN_I2C_SDA, 1);
    esp_rom_delay_us(5);
    gpio_reset_pin(PIN_I2C_SCL);
    gpio_reset_pin(PIN_I2C_SDA);
}

esp_err_t board_init(void)
{
    i2c_bus_recover();

    i2c_master_bus_config_t i2c_cfg = {
        .clk_source = I2C_CLK_SRC_DEFAULT,
        .i2c_port = 0,
        .scl_io_num = PIN_I2C_SCL,
        .sda_io_num = PIN_I2C_SDA,
        .glitch_ignore_cnt = 7,
        .flags.enable_internal_pullup = true,
    };
    ESP_ERROR_CHECK(i2c_new_master_bus(&i2c_cfg, &s_i2c));

    axp2101_init(s_i2c, PIN_PMIC_IRQ);
    axp2101_cmd_init();

    if (pcf85063_init(s_i2c) != ESP_OK) {
        ESP_LOGW(TAG, "RTC not reachable; relying on the ESP timer");
    }

    spi_bus_config_t bus_cfg = {
        .mosi_io_num = PIN_EPD_MOSI,
        .miso_io_num = -1,
        .sclk_io_num = PIN_EPD_SCK,
        .quadwp_io_num = -1,
        .quadhd_io_num = -1,
        .max_transfer_sz = 1200 * 825 / 2 + 100,
    };
    ESP_ERROR_CHECK(spi_bus_initialize(SPI2_HOST, &bus_cfg, SPI_DMA_CH_AUTO));

    epaper_config_t ep = {
        .spi_host = SPI2_HOST,
        .pin_cs = PIN_EPD_CS,
        .pin_dc = PIN_EPD_DC,
        .pin_rst = PIN_EPD_RST,
        .pin_busy = PIN_EPD_BUSY,
        .pin_cs1 = -1,
        .pin_enable = -1,
    };
    epaper_init(&ep);

    gpio_config_t led = {.mode = GPIO_MODE_OUTPUT, .pin_bit_mask = 1ULL << PIN_LED_GREEN};
    gpio_config(&led);
    board_led_activity(false);
    return ESP_OK;
}

int board_battery_mv(void)
{
    return axp2101_get_battery_voltage();
}

// The fuel gauge freezes while the PMIC sleeps, so derive the level from the voltage.
int board_battery_percent(void)
{
    int mv = axp2101_get_battery_voltage();
    if (mv < BATTERY_PRESENT_MIN_MV) {
        return -1;
    }
    if (mv >= 4200) {
        return 100;
    }
    if (mv <= 3300) {
        return 0;
    }
    return (mv - 3300) * 100 / (4200 - 3300);
}

bool board_usb_connected(void)
{
    return axp2101_is_usb_connected();
}

void board_led_activity(bool on)
{
    gpio_set_level(PIN_LED_GREEN, on ? 0 : 1);  // active low
}

void board_prepare_for_sleep(void)
{
    epaper_enter_deepsleep();
    axp2101_basic_sleep_start();
}
