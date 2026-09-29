#!/usr/bin/env python3
"""Bootstrap the exact LiteRT native SDK consumed by the Android C bridge.

The Android Maven AAR publishes libLiteRt.so but not the C headers used by
app/src/main/jni/litert/bridge.cpp. Keep both halves version-locked:

- native libraries: com.google.ai.edge.litert:litert:2.1.5
- C headers/source: google-ai-edge/LiteRT v2.1.5 at the pinned commit below

The output directory is gitignored. This script stages and verifies everything
before replacing an existing SDK directory so an interrupted download cannot
leave a half-populated native toolchain behind.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import uuid
import zipfile

LITERT_VERSION = "2.1.5"
MAVEN_AAR_URL = (
    "https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/"
    f"litert/{LITERT_VERSION}/litert-{LITERT_VERSION}.aar"
)
MAVEN_AAR_SHA256 = "a162d1ddbdad87c002b7ec7eb31a703f2761335e693f292f94091b3569d8aa37"

SOURCE_REPOSITORY = "https://github.com/google-ai-edge/LiteRT.git"
SOURCE_TAG = "v2.1.5"
SOURCE_COMMIT = "9d26e89d88ef8785b6a1e54ec41ac8add215a125"
SOURCE_TREE_GIT_SHA = "c4083fc765566a93fcb76cf355cc515be8bd3763"
BUILD_CONFIG_SOURCE = "litert/build_common/config/build_config_gpu_npu.h"
BUILD_CONFIG_SHA256 = "6e69a0cab0a4743d12c5c365784b0faed0d66e39f83b6abe0338e525e508e493"

# Only ABIs configured by app/build.gradle.kts are materialized and verified.
LIBRARY_SHA256 = {
    "arm64-v8a": "366e3e040b00692158f9f8f9105870672c93348a3d8e9024120b40045a074b0b",
    "x86_64": "6d5b2f35d536a3b2d38b26d26328cc9c259133ef2aa0413ec554cd7ef84f6604",
}

REQUIRED_HEADERS = (
    "litert/c/litert_common.h",
    "litert/c/litert_compiled_model.h",
    "litert/c/litert_environment.h",
    "litert/c/litert_model.h",
    "litert/c/litert_tensor_buffer.h",
    "litert/c/litert_tensor_buffer_requirements.h",
    "litert/c/litert_tensor_buffer_types.h",
    "litert/build_common/build_config.h",
)

MARKER_NAME = ".bootstrap.json"
MARKER_SCHEMA = 2


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


GENERATED_BUILD_CONFIG = "build_common/build_config.h"


def git_blob_sha(path: Path) -> bytes:
    data = path.read_bytes()
    header = f"blob {len(data)}\0".encode("ascii")
    return hashlib.sha1(header + data).digest()


def git_tree_sha(root: Path, relative_root: Path = Path()) -> str:
    """Reconstruct Git's tree object ID for the copied LiteRT source tree."""
    entries: list[tuple[bytes, bytes]] = []
    for path in root.iterdir():
        relative = relative_root / path.name
        if relative.as_posix() == GENERATED_BUILD_CONFIG:
            continue

        name = path.name.encode("utf-8")
        if path.is_dir():
            object_id = bytes.fromhex(git_tree_sha(path, relative))
            mode = b"40000"
            sort_key = name + b"/"
        elif path.is_file():
            object_id = git_blob_sha(path)
            mode = b"100755" if path.stat().st_mode & 0o111 else b"100644"
            sort_key = name
        else:
            raise RuntimeError(f"unsupported file type in LiteRT source tree: {path}")

        entries.append((sort_key, mode + b" " + name + b"\0" + object_id))

    body = b"".join(entry for _, entry in sorted(entries, key=lambda item: item[0]))
    header = f"tree {len(body)}\0".encode("ascii")
    return hashlib.sha1(header + body).hexdigest()


def run(*args: str, cwd: Path | None = None) -> None:
    subprocess.run(args, cwd=cwd, check=True)


def expected_marker() -> dict[str, object]:
    return {
        "schema": MARKER_SCHEMA,
        "litertVersion": LITERT_VERSION,
        "maven": {
            "url": MAVEN_AAR_URL,
            "sha256": MAVEN_AAR_SHA256,
        },
        "source": {
            "repository": SOURCE_REPOSITORY,
            "tag": SOURCE_TAG,
            "commit": SOURCE_COMMIT,
            "gitTree": SOURCE_TREE_GIT_SHA,
        },
        "libraries": LIBRARY_SHA256,
        "buildConfig": {
            "source": BUILD_CONFIG_SOURCE,
            "sha256": BUILD_CONFIG_SHA256,
        },
    }


def validate_output(output: Path, *, verbose: bool = False) -> tuple[bool, str]:
    marker_path = output / MARKER_NAME
    if not marker_path.is_file():
        return False, f"missing {marker_path}"

    try:
        marker = json.loads(marker_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return False, f"invalid marker: {exc}"

    if marker != expected_marker():
        return False, "bootstrap marker does not match the pinned LiteRT inputs"

    for abi, expected_hash in LIBRARY_SHA256.items():
        library = output / abi / "libLiteRt.so"
        if not library.is_file():
            return False, f"missing {library}"
        actual_hash = sha256_file(library)
        if actual_hash != expected_hash:
            return False, f"checksum mismatch for {library}"

    for relative in REQUIRED_HEADERS:
        header = output / relative
        if not header.is_file():
            return False, f"missing {header}"

    source_tree = output / "litert"
    actual_tree_hash = git_tree_sha(source_tree)
    if actual_tree_hash != SOURCE_TREE_GIT_SHA:
        return (
            False,
            "LiteRT source tree mismatch: "
            f"expected {SOURCE_TREE_GIT_SHA}, got {actual_tree_hash}",
        )

    generated_config = source_tree / GENERATED_BUILD_CONFIG
    if sha256_file(generated_config) != BUILD_CONFIG_SHA256:
        return False, "generated LiteRT build_config.h checksum mismatch"

    if verbose:
        print(
            f"LiteRT native SDK {LITERT_VERSION} verified at {output} "
            f"(source {SOURCE_COMMIT[:12]})"
        )
    return True, "ok"


def find_cached_aar() -> Path | None:
    gradle_home = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
    root = (
        gradle_home
        / "caches/modules-2/files-2.1/com.google.ai.edge.litert/litert"
        / LITERT_VERSION
    )
    if not root.is_dir():
        return None

    for candidate in root.glob(f"*/litert-{LITERT_VERSION}.aar"):
        try:
            if sha256_file(candidate) == MAVEN_AAR_SHA256:
                return candidate
        except OSError:
            continue
    return None


def acquire_aar(temp: Path) -> Path:
    cached = find_cached_aar()
    if cached is not None:
        print(f"Using verified Gradle-cached LiteRT AAR: {cached}")
        return cached

    target = temp / f"litert-{LITERT_VERSION}.aar"
    print(f"Downloading LiteRT Maven AAR {LITERT_VERSION}")
    with urllib.request.urlopen(MAVEN_AAR_URL, timeout=60) as response:
        with target.open("wb") as handle:
            shutil.copyfileobj(response, handle)

    actual = sha256_file(target)
    if actual != MAVEN_AAR_SHA256:
        raise RuntimeError(
            "LiteRT Maven AAR checksum mismatch: "
            f"expected {MAVEN_AAR_SHA256}, got {actual}"
        )
    return target


def extract_libraries(aar: Path, stage: Path) -> None:
    with zipfile.ZipFile(aar) as archive:
        for abi, expected_hash in LIBRARY_SHA256.items():
            member = f"jni/{abi}/libLiteRt.so"
            destination = stage / abi / "libLiteRt.so"
            destination.parent.mkdir(parents=True, exist_ok=True)
            try:
                with archive.open(member) as source, destination.open("wb") as target:
                    shutil.copyfileobj(source, target)
            except KeyError as exc:
                raise RuntimeError(f"LiteRT AAR is missing {member}") from exc

            actual = sha256_file(destination)
            if actual != expected_hash:
                raise RuntimeError(
                    f"LiteRT {abi} library checksum mismatch: "
                    f"expected {expected_hash}, got {actual}"
                )


def checkout_headers(temp: Path, stage: Path) -> None:
    source = temp / "LiteRT"
    print(f"Fetching LiteRT headers from pinned commit {SOURCE_COMMIT}")
    run("git", "init", "-q", str(source))
    run("git", "-C", str(source), "remote", "add", "origin", SOURCE_REPOSITORY)
    run(
        "git",
        "-C",
        str(source),
        "-c",
        "protocol.version=2",
        "fetch",
        "-q",
        "--depth=1",
        "--filter=blob:none",
        "origin",
        SOURCE_COMMIT,
    )
    run("git", "-C", str(source), "sparse-checkout", "init", "--cone")
    run("git", "-C", str(source), "sparse-checkout", "set", "litert")
    run("git", "-C", str(source), "checkout", "-q", "--detach", "FETCH_HEAD")

    actual_commit = subprocess.check_output(
        ["git", "-C", str(source), "rev-parse", "HEAD"],
        text=True,
    ).strip()
    if actual_commit != SOURCE_COMMIT:
        raise RuntimeError(
            f"LiteRT source commit mismatch: expected {SOURCE_COMMIT}, got {actual_commit}"
        )

    actual_tree = subprocess.check_output(
        ["git", "-C", str(source), "rev-parse", "HEAD:litert"],
        text=True,
    ).strip()
    if actual_tree != SOURCE_TREE_GIT_SHA:
        raise RuntimeError(
            "LiteRT source tree mismatch: "
            f"expected {SOURCE_TREE_GIT_SHA}, got {actual_tree}"
        )

    shutil.copytree(source / "litert", stage / "litert")

    # Upstream generates this file from one of four checked-in configurations.
    # The v2.1.5 BUILD target's default selects GPU+NPU, matching the Android AAR.
    config = stage / BUILD_CONFIG_SOURCE
    generated = stage / "litert" / GENERATED_BUILD_CONFIG
    if not config.is_file():
        raise RuntimeError(f"LiteRT source is missing {config}")
    shutil.copy2(config, generated)


def write_marker(stage: Path) -> None:
    marker = stage / MARKER_NAME
    marker.write_text(
        json.dumps(expected_marker(), indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def atomic_install(stage: Path, output: Path) -> None:
    old = output.with_name(f"{output.name}.old-{uuid.uuid4().hex}")
    moved_old = False

    try:
        if output.exists():
            os.replace(output, old)
            moved_old = True
        os.replace(stage, output)
    except Exception:
        if moved_old and not output.exists() and old.exists():
            os.replace(old, output)
        raise
    else:
        if moved_old:
            shutil.rmtree(old, ignore_errors=True)


def bootstrap(output: Path) -> None:
    ok, _ = validate_output(output, verbose=True)
    if ok:
        return

    output.parent.mkdir(parents=True, exist_ok=True)
    stage = output.with_name(f"{output.name}.staging-{uuid.uuid4().hex}")

    with tempfile.TemporaryDirectory(prefix="rikkahub-litert-") as temp_name:
        temp = Path(temp_name)
        try:
            stage.mkdir(parents=False)
            aar = acquire_aar(temp)
            extract_libraries(aar, stage)
            checkout_headers(temp, stage)
            write_marker(stage)

            ok, reason = validate_output(stage)
            if not ok:
                raise RuntimeError(f"staged LiteRT SDK failed verification: {reason}")

            atomic_install(stage, output)
        finally:
            if stage.exists():
                shutil.rmtree(stage, ignore_errors=True)

    print(
        f"LiteRT native SDK {LITERT_VERSION} verified and installed at {output} "
        f"(source {SOURCE_COMMIT[:12]})"
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
        help="Destination litert_cc_sdk directory",
    )
    parser.add_argument(
        "--verify-only",
        action="store_true",
        help="Verify the existing SDK without downloading or modifying it",
    )
    args = parser.parse_args()

    output = args.output.resolve()
    if args.verify_only:
        ok, reason = validate_output(output, verbose=True)
        if not ok:
            print(f"LiteRT native SDK verification failed: {reason}", file=sys.stderr)
            return 1
        return 0

    try:
        bootstrap(output)
    except (OSError, RuntimeError, subprocess.CalledProcessError, zipfile.BadZipFile) as exc:
        print(f"LiteRT native SDK bootstrap failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
