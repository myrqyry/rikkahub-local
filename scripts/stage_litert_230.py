#!/usr/bin/env python3
"""Stage the pinned 2.3.0 LiteRT native C SDK and the exact Maven AAR binaries.

No global SDK installation, no stale ABI blending, and no downloads after
receipt validation. Git ignores app/src/main/jni/litert/litert_cc_sdk/.
"""
import hashlib
import io
import json
from pathlib import Path
import shutil
import sys
import tempfile
import urllib.request
import zipfile

VERSION = "2.3.0"
RELEASE = "https://github.com/google-ai-edge/LiteRT/releases/download/v2.3.0/litert_cc_sdk.zip"
SDK_SHA256 = "44ebb188ae1a510f233639fa499928fdaaba46436a153a2dbfbb9d87d59728d2"
AAR_URL = "https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert/2.3.0/litert-2.3.0.aar"
AAR_SHA256 = "9508c6e9142646f0f2bc1fe68b3925fb470ee7ddd29114540339a04a8ab189dc"
ABI_SHA256 = {
    "arm64-v8a": "e5b7e3413a8b428b1a1b669f368cf613058868bc7b63e10d0b9c2e6ba55c97d8",
    "x86_64": "702e378bc626da1e6e0dadf4ff2520c94be48dc89147db8a5cb4139d2213be66",
}
SOURCE_HEADER_SHA256 = {
    "litert/c/litert_compiled_model.h": "e613c98b5299b0c3c130c883963d29f98a6f0b2dccc66c73b8a10bdccdcac13b",
    "litert/c/litert_model.h": "c409c985fc79b9f967890e475e28ab77c1fe7776874197a598438a7b2d916d8b",
    "litert/c/litert_environment.h": "a83820ef9e68bd175db75a7ef155ce67f5bab2e7ad04e6b046affe9ee3366b3d",
    "litert/build_common/build_config.h.in": "50201bd0f8b5b504322f2fd65c9535c685f633b77d6526e400adaaa245ac46cc",
}
ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "app/src/main/jni/litert/litert_cc_sdk"
RECEIPT = DEST / ".bootstrap.json"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def get(url, expected):
    with urllib.request.urlopen(url, timeout=90) as response:
        content = response.read()
    found = digest(content)
    if found != expected:
        raise RuntimeError(f"Checksum mismatch for {url}: {found}")
    return content


def is_staged():
    if not RECEIPT.is_file():
        return False
    try:
        receipt = json.loads(RECEIPT.read_text())
        if receipt.get("litertVersion") != VERSION or receipt.get("sdkSha256") != SDK_SHA256 or receipt.get("aarSha256") != AAR_SHA256:
            return False
        for abi, expected in ABI_SHA256.items():
            path = DEST / abi / "libLiteRt.so"
            if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
                return False
        for relative, expected in SOURCE_HEADER_SHA256.items():
            header = DEST / relative
            if not header.is_file() or hashlib.sha256(header.read_bytes()).hexdigest() != expected:
                return False
        return True
    except (ValueError, OSError):
        return False


def main():
    if is_staged():
        print("LiteRT 2.3.0 native SDK verified and already staged.")
        return
    sdk = get(RELEASE, SDK_SHA256)
    aar = get(AAR_URL, AAR_SHA256)
    with zipfile.ZipFile(io.BytesIO(sdk)) as source, zipfile.ZipFile(io.BytesIO(aar)) as library:
        with tempfile.TemporaryDirectory() as temporary:
            tmp = Path(temporary)
            source.extractall(tmp)
            sdk_root = tmp / "litert_cc_sdk"
            if not (sdk_root / "litert/c/litert_compiled_model.h").is_file():
                raise RuntimeError("Incomplete official LiteRT C SDK archive")
            for abi, expected in ABI_SHA256.items():
                data = library.read(f"jni/{abi}/libLiteRt.so")
                if digest(data) != expected:
                    raise RuntimeError(f"LiteRT runtime mismatch: {abi}")
                native = DEST / abi / "libLiteRt.so"
                native.parent.mkdir(parents=True, exist_ok=True)
                staged = native.with_suffix(".so.staging")
                staged.write_bytes(data)
                staged.replace(native)
            target_c = DEST / "litert/c"
            if target_c.is_dir():
                shutil.rmtree(target_c)
            shutil.copytree(sdk_root / "litert/c", target_c)
            source_config = sdk_root / "litert/build_common/build_config.h.in"
            target_config = DEST / "litert/build_common/build_config.h.in"
            target_config.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source_config, target_config)
    RECEIPT.write_text(json.dumps({
        "litertVersion": VERSION,
        "sdkSha256": SDK_SHA256,
        "aarSha256": AAR_SHA256,
        "libraries": ABI_SHA256,
        "source": RELEASE,
        "maven": AAR_URL,
    }, indent=2) + "\n")
    print("LiteRT 2.3.0 staged from SHA-verified official C SDK and Maven AAR.")


if __name__ == "__main__":
    main()
