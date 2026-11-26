#pragma once

#include <stdbool.h>
#include <stddef.h>

// Extracts and URL-decodes `key` from an application/x-www-form-urlencoded body.
// Returns false if the key is missing or the value does not fit in `cap` bytes.
bool form_get(const char *body, const char *key, char *out, size_t cap);
