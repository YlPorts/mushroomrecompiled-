#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#define SMB3_ROM_SIZE 393232u
#define SMB3_ROM_CRC32 0x2e6301edu
#define SMB3_ROM_SHA256 "4377a7f5e6eb50bdd2ac6f249bf1a7085500aca8eb41f38545c3a2731c51a579"
void smb3_sha256(const uint8_t *data, size_t size, uint8_t digest[32]);
uint32_t smb3_crc32(const uint8_t *data, size_t size);
bool smb3_rom_validate(const uint8_t *data, size_t size);
