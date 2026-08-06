#ifndef VAPE421_LOADER_BOOTSTRAP_H
#define VAPE421_LOADER_BOOTSTRAP_H

#include <stdint.h>

#define VAPE421_BOOTSTRAP_MAGIC 0x54423456u
#define VAPE421_BOOTSTRAP_VERSION 3u
#define VAPE421_BOOTSTRAP_STATUS_CREATED 1u
#define VAPE421_BOOTSTRAP_STATUS_CONSUMED 2u
#define VAPE421_BOOTSTRAP_STATUS_FAILED 3u

#pragma pack(push, 1)
typedef struct Vape421BootstrapV3 {
    uint32_t magic;
    uint16_t version;
    uint16_t structure_size;
    uint32_t target_pid;
    uint16_t controller_port;
    uint16_t reserved0;
    uint8_t reserved[8];
    uint32_t status;
} Vape421BootstrapV3;
#pragma pack(pop)

typedef char Vape421BootstrapV3_size_must_be_28[
        sizeof(Vape421BootstrapV3) == 28 ? 1 : -1];

int vape_loader_bootstrap_initialize(void);
int vape_loader_bootstrap_failed(void);
void vape_loader_report_progress(int step);
void vape_loader_report_log(const char *message);
void vape_loader_report_completed(void);
void vape_loader_report_failure(const char *message);
void vape_loader_signal_injection_event(int success);
void vape_loader_bootstrap_clear(void);

#endif
