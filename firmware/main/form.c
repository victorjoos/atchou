#include "form.h"

#include <string.h>

static int hex_val(char c)
{
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

bool form_get(const char *body, const char *key, char *out, size_t cap)
{
    size_t klen = strlen(key);
    const char *p = body;
    while (p && *p) {
        if (strncmp(p, key, klen) == 0 && p[klen] == '=') {
            p += klen + 1;
            size_t n = 0;
            while (*p && *p != '&') {
                char c = *p;
                if (c == '+') {
                    c = ' ';
                    p++;
                } else if (c == '%' && hex_val(p[1]) >= 0 && hex_val(p[2]) >= 0) {
                    c = (char)(hex_val(p[1]) * 16 + hex_val(p[2]));
                    p += 3;
                } else {
                    p++;
                }
                if (n + 1 >= cap) {
                    return false;
                }
                out[n++] = c;
            }
            out[n] = '\0';
            return true;
        }
        p = strchr(p, '&');
        if (p) p++;
    }
    return false;
}
