#include <assert.h>
#include <stdio.h>
#include <string.h>

#include "../main/form.h"
#include "../main/sleep_plan.h"

int main(void)
{
    time_t t;
    assert(sleep_parse_utc("1970-01-01T00:00:00Z", &t) && t == 0);
    assert(sleep_parse_utc("2024-02-29T12:30:45Z", &t) && t == 1709209845);
    assert(sleep_parse_utc("2026-07-01T06:00:00Z", &t) && t == 1782885600);
    assert(!sleep_parse_utc("2026-07-01 06:00:00", &t));
    assert(!sleep_parse_utc("2026-13-01T06:00:00Z", &t));
    assert(!sleep_parse_utc(NULL, &t));

    assert(sleep_seconds_until(1000, 1000 + 7200) == 7200);
    assert(sleep_seconds_until(1000, 1000 + 5) == SLEEP_MIN_SECONDS);
    assert(sleep_seconds_until(1000, 500) == SLEEP_MIN_SECONDS);
    assert(sleep_seconds_until(0, 10 * 86400) == SLEEP_MAX_SECONDS);

    assert(sleep_failure_backoff(1, 21600) == 900);
    assert(sleep_failure_backoff(2, 21600) == 3600);
    assert(sleep_failure_backoff(7, 21600) == 21600);

    char v[16];
    const char *body = "ssid=Caf%C3%A9+Wi-Fi&pass=a%26b%3Dc&url=https%3A%2F%2Fx.fr&tok=";
    assert(form_get(body, "ssid", v, sizeof v) && !strcmp(v, "Caf\xC3\xA9 Wi-Fi"));
    assert(form_get(body, "pass", v, sizeof v) && !strcmp(v, "a&b=c"));
    assert(form_get(body, "url", v, sizeof v) && !strcmp(v, "https://x.fr"));
    assert(form_get(body, "tok", v, sizeof v) && !strcmp(v, ""));
    assert(!form_get(body, "nope", v, sizeof v));
    assert(!form_get(body, "url", v, 5));
    assert(!form_get(body, "ss", v, sizeof v));

    puts("ok");
    return 0;
}
