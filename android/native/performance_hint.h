#pragma once
#include <stdint.h>
void performance_hint_open(int64_t budget_ns);
void performance_hint_report(int64_t work_ns);
void performance_hint_close(void);
