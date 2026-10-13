#!/usr/bin/env python3
"""Stage Scylla's pinned LiteRT 2.2.0 under an isolated ELF SONAME.

RikkaHub's LiteRT 2.1.5 retains libLiteRt.so. Scylla links to
libScyllaLiteRt.so and therefore does not replace another runtime.
Uses explicit SHA-256 for both upstream Google binaries; no trust in
partially downloaded or older staged files.
"""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request

VERSION = "2.2.0"
SONAME = "libScyllaLiteRt.so"
SOURCES = {
    "arm64-v8a": ("android_arm64", "e27ff49de6e606d3b7d3c3b16398672097021e971909c13299bf8c38ca309088"),
    "x86_64": ("android_x86_64", "16a6ecdc1324010e8e758aa6687aa837decbb4a6ff5fa4c11b487daca5668667"),
}


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--patchelf", default=os.environ.get("SCYLLA_PATCHELF", "patchelf"))
    args = parser.parse_args()
    patchelf = shutil.which(args.patchelf)
    if not patchelf:
        raise SystemExit("Scylla LiteRT requires patchelf on the build host (e.g. apt install patchelf)")
    for abi, (platform, expected) in SOURCES.items():
        directory = args.output / abi
        directory.mkdir(parents=True, exist_ok=True)
        final = directory / SONAME
        receipt = directory / "source-sha256.txt"
        if final.is_file() and receipt.is_file() and receipt.read_text().strip() == expected:
            if subprocess.check_output([patchelf, "--print-soname", str(final)], text=True).strip() == SONAME:
                continue
        url = f"https://storage.googleapis.com/litert/binaries/{VERSION}/{platform}/libLiteRt.so"
        with tempfile.TemporaryDirectory(dir=directory) as temp:
            staged = Path(temp) / "libLiteRt.so"
            with urllib.request.urlopen(url, timeout=90) as response, staged.open("wb") as output:
                shutil.copyfileobj(response, output)
            actual = digest(staged)
            if actual != expected:
                raise RuntimeError(f"LiteRT {VERSION} {abi} SHA-256 mismatch: {actual}")
            subprocess.run([patchelf, "--set-soname", SONAME, str(staged)], check=True)
            reported = subprocess.check_output([patchelf, "--print-soname", str(staged)], text=True).strip()
            if reported != SONAME:
                raise RuntimeError("Failed to isolate Scylla's LiteRT SONAME")
            staged.replace(final)
            receipt.write_text(expected + "\n")
        print(f"Staged Scylla LiteRT {VERSION} {abi}: {final}")


if __name__ == "__main__":
    main()
