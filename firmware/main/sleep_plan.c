#include "sleep_plan.h"

#include <stdio.h>

// Days since 1970-01-01 for a proleptic Gregorian date.
static int64_t days_from_civil(int y, unsigned m, unsigned d)
{
    y -= m <= 2;
    int era = (y >= 0 ? y : y - 399) / 400;
    unsigned yoe = (unsigned)(y - era * 400);
    unsigned doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
    unsigned doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    return (int64_t)era * 146097 + (int64_t)doe - 719468;
}

bool sleep_parse_utc(const char *s, time_t *out)
{
    if (!s) {
        return false;
    }
    int y, mo, d, h, mi, se;
    char z = 0;
    if (sscanf(s, "%4d-%2d-%2dT%2d:%2d:%2d%c", &y, &mo, &d, &h, &mi, &se, &z) != 7 || z != 'Z') {
        return false;
    }
    if (mo < 1 || mo > 12 || d < 1 || d > 31 || h > 23 || mi > 59 || se > 60 || y < 1970) {
        return false;
    }
    *out = (time_t)(days_from_civil(y, (unsigned)mo, (unsigned)d) * 86400 + h * 3600 + mi * 60 + se);
    return true;
}

uint32_t sleep_seconds_until(time_t now, time_t wake)
{
    if (wake <= now) {
        return SLEEP_MIN_SECONDS;
    }
    int64_t delta = (int64_t)wake - (int64_t)now;
    if (delta < SLEEP_MIN_SECONDS) {
        return SLEEP_MIN_SECONDS;
    }
    if (delta > SLEEP_MAX_SECONDS) {
        return SLEEP_MAX_SECONDS;
    }
    return (uint32_t)delta;
}

uint32_t sleep_failure_backoff(unsigned failures, uint32_t fallback)
{
    if (failures <= 1) {
        return 15 * 60;
    }
    if (failures == 2) {
        return 3600;
    }
    return fallback;
}
