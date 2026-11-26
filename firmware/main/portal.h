#include <stdint.h>

#pragma once

// Runs the captive-portal setup: shows the setup screen, serves the form and reboots after saving.
// Returns only if the portal timed out without a submission.
void portal_run(uint8_t *panel_buf);
