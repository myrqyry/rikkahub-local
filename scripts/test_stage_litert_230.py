import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("stage_litert_230.py")


def load_stage_module():
    spec = importlib.util.spec_from_file_location("stage_litert_230_test_target", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class StageLiteRt230Test(unittest.TestCase):
    def make_staged_tree(self, include_build_config: bool):
        module = load_stage_module()
        temporary = tempfile.TemporaryDirectory()
        root = Path(temporary.name)
        module.DEST = root
        module.RECEIPT = root / ".bootstrap.json"

        library = b"test-litert-library"
        header = b"test-litert-header"
        config = b"test-build-config"
        module.ABI_SHA256 = {"arm64-v8a": hashlib.sha256(library).hexdigest()}
        module.SOURCE_HEADER_SHA256 = {
            "litert/c/litert_model.h": hashlib.sha256(header).hexdigest(),
        }
        module.BUILD_CONFIG_SHA256 = hashlib.sha256(config).hexdigest()

        native = root / "arm64-v8a/libLiteRt.so"
        native.parent.mkdir(parents=True)
        native.write_bytes(library)

        model_header = root / "litert/c/litert_model.h"
        model_header.parent.mkdir(parents=True)
        model_header.write_bytes(header)

        if include_build_config:
            build_config = root / "litert/build_common/build_config.h"
            build_config.parent.mkdir(parents=True)
            build_config.write_bytes(config)

        module.RECEIPT.write_text(
            json.dumps(
                {
                    "litertVersion": module.VERSION,
                    "sdkSha256": module.SDK_SHA256,
                    "aarSha256": module.AAR_SHA256,
                    "buildConfigSha256": module.BUILD_CONFIG_SHA256,
                    "libraries": module.ABI_SHA256,
                    "source": module.RELEASE,
                    "maven": module.AAR_URL,
                }
            )
        )
        return module, temporary

    def test_is_staged_rejects_missing_generated_build_config(self):
        module, temporary = self.make_staged_tree(include_build_config=False)
        self.addCleanup(temporary.cleanup)
        self.assertFalse(module.is_staged())

    def test_is_staged_accepts_complete_verified_tree(self):
        module, temporary = self.make_staged_tree(include_build_config=True)
        self.addCleanup(temporary.cleanup)
        self.assertTrue(module.is_staged())


if __name__ == "__main__":
    unittest.main()
