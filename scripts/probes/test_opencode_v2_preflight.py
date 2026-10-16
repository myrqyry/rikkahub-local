import importlib.util
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

p = Path(__file__).with_name("opencode-v2-preflight.py")
spec = importlib.util.spec_from_file_location("opencode_v2_preflight", p)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def original():
    return {
        "provider": {
            "rikka-pixel": {
                "npm": "@ai-sdk/openai-compatible",
                "options": {"baseURL": "http://127.0.0.1:18080/v1", "apiKey": "must-not-copy"},
                "models": {
                    "litert/LFM2.5-1.2B-Instruct_int4.litertlm": {
                        "tool_call": False,
                        "limit": {"context": 4096, "output": 256},
                    }
                },
            },
            "private-provider": {"options": {"apiKey": "other-secret"}},
        },
        "plugin": ["old-plugin"],
        "mcp": {"private": {"headers": {"Authorization": "secret"}}},
    }


class V2PreflightTest(unittest.TestCase):
    def test_pixel_candidate_uses_native_v2_model_and_never_copies_secrets(self):
        candidate = m.pixel_preview(original())
        p = candidate["providers"]["rikka-pixel"]
        self.assertEqual(p["package"], "@opencode/ai/providers/openai-compatible")
        model = p["models"]["lfm2.5-1.2b"]
        self.assertEqual(model["modelID"], "litert/LFM2.5-1.2B-Instruct_int4.litertlm")
        self.assertEqual(model["capabilities"], {"tools": False, "input": ["text"], "output": ["text"]})
        self.assertEqual(candidate["permissions"][0]["effect"], "ask")
        self.assertNotIn("secret", json.dumps(candidate))
        self.assertNotIn("private-provider", json.dumps(candidate))
        self.assertNotIn("old-plugin", json.dumps(candidate))

    def test_changes_to_verified_endpoint_or_tool_support_fail_closed(self):
        bad = original()
        bad["provider"]["rikka-pixel"]["options"]["baseURL"] = "http://0.0.0.0:18080/v1"
        with self.assertRaises(ValueError):
            m.pixel_preview(bad)
        bad = original()
        bad["provider"]["rikka-pixel"]["models"]["litert/LFM2.5-1.2B-Instruct_int4.litertlm"]["tool_call"] = True
        with self.assertRaises(ValueError):
            m.pixel_preview(bad)

    def test_candidate_cannot_clobber_existing_source_or_output(self):
        with TemporaryDirectory() as folder:
            src = Path(folder) / "active.json"
            src.write_text(json.dumps(original()))
            dst = Path(folder) / "candidate.json"
            candidate = m.pixel_preview(original())
            with self.assertRaises(ValueError):
                m.write_candidate(candidate, src, src)
            m.write_candidate(candidate, dst, src)
            self.assertEqual(json.loads(dst.read_text()), candidate)
            self.assertEqual(json.loads(src.read_text()), original())
            with self.assertRaises(FileExistsError):
                m.write_candidate(candidate, dst, src)

    def test_html_server_info_is_not_mistaken_for_v2(self):
        import urllib.request
        from unittest.mock import patch
        from email.message import Message

        class Response:
            def __init__(self, content_type, text):
                self.headers = Message()
                self.headers["Content-Type"] = content_type
                self.text = text
            def __enter__(self): return self
            def __exit__(self, *args): return False
            def read(self, size): return self.text.encode()

        with patch.object(urllib.request, "urlopen", return_value=Response("text/html", "<html></html>")):
            self.assertEqual(m.probe_server("http://127.0.0.1:4196")[0], "not-v2")
        with patch.object(urllib.request, "urlopen", return_value=Response("application/json", '{"urls":["http://local"],"ready":true}')):
            self.assertEqual(m.probe_server("http://127.0.0.1:4196")[0], "v2-candidate")
        with patch.object(urllib.request, "urlopen", return_value=Response("application/json", '{"healthy":true}')):
            self.assertEqual(m.probe_server("http://127.0.0.1:4196")[0], "unknown")


if __name__ == "__main__":
    unittest.main()
