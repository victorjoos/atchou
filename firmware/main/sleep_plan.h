#pragma once

#include <stdbool.h>
#include <stdint.h>
#include <time.h>

#define SLEEP_MIN_SECONDS 60u
#define SLEEP_MAX_SECONDS (25u * 3600u)

// Parses "YYYY-MM-DDTHH:MM:SSZ" (UTC). Returns false on any deviation.
bool sleep_parse_utc(const char *s, time_t *out);

// Seconds to sleep until `wake`, clamped to [SLEEP_MIN_SECONDS, SLEEP_MAX_SECONDS].
uint32_t sleep_seconds_until(time_t now, time_t wake);

// Sleep after `failures` consecutive failed cycles (>= 1): 15 min, 1 h, then fallback.
uint32_t sleep_failure_backoff(unsigned failures, uint32_t fallback);
