#!/usr/bin/env python3
"""Materialize an encrypted private ROM without echoing credentials or signed URLs."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request
from validate_rom import validate

def main():
    url=os.environ.get("ROM_DOWNLOAD_URL", "")
    password=os.environ.get("ROM_ARCHIVE_PASSWORD", "")
    if not url or not password:
        sys.exit("Private ROM missing: configure ROM_DOWNLOAD_URL (HTTPS URL to encrypted .nes.enc) and ROM_ARCHIVE_PASSWORD in the android-private-build environment. The ROM is too large for a GitHub Secret. See docs/ANDROID.md. No APK was generated.")
    if urllib.parse.urlparse(url).scheme != "https":
        sys.exit("ROM_DOWNLOAD_URL must use HTTPS.")
    target=Path(sys.argv[1]).resolve();target.parent.mkdir(parents=True,exist_ok=True)
    try:
        with tempfile.TemporaryDirectory(prefix="private-rom-",dir=target.parent) as private:
            cipher=Path(private)/"input.enc"
            with urllib.request.urlopen(url,timeout=60) as response:
                if urllib.parse.urlparse(response.url).scheme!="https":
                    raise ValueError("Unsafe redirect")
                data=response.read(2*1024*1024+1)
            if len(data)>2*1024*1024:
                raise ValueError("Encrypted ROM is too large")
            cipher.write_bytes(data);cipher.chmod(0o600)
            plain=Path(private)/"input.nes"
            subprocess.run(["openssl","enc","-d","-aes-256-cbc","-pbkdf2","-iter","200000","-pass","env:ROM_ARCHIVE_PASSWORD","-in",str(cipher),"-out",str(plain)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            validate(plain);plain.chmod(0o600);plain.replace(target)
    except Exception:
        sys.exit("Private ROM retrieval, decryption or exact validation failed. Check the private URL, password and compatible original ROM. No placeholder APK was generated.")
    print("Private ROM downloaded, decrypted and exactly validated; contents will not be uploaded.")

if __name__=="__main__":main()
