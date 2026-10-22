#!/usr/bin/env python3
"""Validate the unmodified private build input. Never accepts only a payload hash."""
import argparse
import hashlib
from pathlib import Path
import zlib

SIZE = 393232
CRC32 = 0x2E6301ED
SHA256 = "4377a7f5e6eb50bdd2ac6f249bf1a7085500aca8eb41f38545c3a2731c51a579"

def validate(path):
    path = Path(path)
    if not path.is_file():
        raise ValueError("Missing private ROM. Supply --rom '/path/Super Mario Bros. 3 (USA) (Rev 1).nes'.")
    if path.stat().st_size != SIZE:
        raise ValueError(f"ROM size must be {SIZE} bytes.")
    data = path.read_bytes()
    if data[:4] != b"NES\x1a" or data[4:6] != bytes([16, 16]) or data[6] & 4 or ((data[6] >> 4) | (data[7] & 0xf0)) != 4:
        raise ValueError("Expected iNES MMC3, 256 KiB PRG + 128 KiB CHR, without a trainer.")
    crc = zlib.crc32(data[16:])
    if crc != CRC32:
        raise ValueError(f"Payload CRC32 is {crc:08X}; expected {CRC32:08X}.")
    sha = hashlib.sha256(data).hexdigest()
    if sha != SHA256:
        raise ValueError(f"Full SHA-256 is {sha.upper()}; expected {SHA256.upper()}. The original ROM will not be modified.")
    return sha

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("rom", type=Path)
    args = parser.parse_args()
    try:
        print("ROM validated: " + validate(args.rom))
    except ValueError as exc:
        parser.exit(2, str(exc) + "\n")
