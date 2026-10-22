#!/usr/bin/env python3
"""Build NESRecomp for the host, then generate private C for NDK compilation."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from validate_rom import validate

ROOT = Path(__file__).resolve().parents[1]
INPUTS = [ROOT / "game.toml", ROOT / "docs/smb3-cycle-seeds.txt"]
INPUTS += sorted((ROOT / "tools/nesrecomp-src/recompiler").rglob("*.c"))
INPUTS += sorted((ROOT / "tools/nesrecomp-src/recompiler").rglob("*.h"))

def run(*args, cwd=ROOT):
    subprocess.run([str(a) for a in args], cwd=cwd, check=True)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rom", required=True, type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "generated")
    args = parser.parse_args()
    try:
        rom_sha = validate(args.rom)
    except ValueError as exc:
        parser.exit(2, str(exc) + "\n")
    fingerprint = hashlib.sha256()
    for path in INPUTS:
        fingerprint.update(str(path.relative_to(ROOT)).encode())
        fingerprint.update(path.read_bytes())
    manifest = {"rom_sha256": rom_sha, "inputs_sha256": fingerprint.hexdigest()}
    output = args.output.resolve()
    previous = output / "manifest.json"
    if previous.is_file() and json.loads(previous.read_text()) == manifest and (output / "smb3_cyc.c").is_file():
        print("Private code generation is current.")
        return
    host = ROOT / "build/host-recompiler"
    run("cmake", "-S", ROOT / "tools/nesrecomp-src/recompiler", "-B", host, "-DCMAKE_BUILD_TYPE=Release")
    run("cmake", "--build", host, "--config", "Release", "--parallel", "4")
    compiler = host / ("NESRecomp.exe" if os.name == "nt" else "NESRecomp")
    if not compiler.exists():
        compiler = host / "Release/NESRecomp.exe"
    # The generator writes relative to cwd. Stage privately; never publish partial C.
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="smb3-codegen-", dir=ROOT / "build") as stage:
        stage = Path(stage)
        run(compiler, args.rom.resolve(), "--game", INPUTS[0], "--cycle-seed-file", INPUTS[1], cwd=stage)
        generated = stage / "generated"
        if not (generated / "smb3_cyc.c").is_file():
            raise RuntimeError("NESRecomp did not produce the real game dispatch.")
        (generated / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
        if output.exists():
            shutil.rmtree(output)
        shutil.move(str(generated), output)
    print("Real cycle-accurate C generated privately. No ROM is packaged in the APK.")

if __name__ == "__main__":
    main()
