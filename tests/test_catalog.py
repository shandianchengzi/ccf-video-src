import json
from pathlib import Path
import unittest
from scripts.catalog import classify, iterate, normalize, validate


def row(i, title="芯片设计", **extra):
    return dict(id=str(i), title=title, date=1609459200000, cover="", view_count="12", visible_tag="会员", **extra)


class FakeClient:
    def __init__(self, pages): self.pages = iter(pages)
    def post(self, path, form): return next(self.pages)


class CatalogTests(unittest.TestCase):
    def test_server_page_size_cap_does_not_truncate(self):
        c = FakeClient([{"data": [row(1), row(2)], "count": 3}, {"data": [row(3)], "count": 3}])
        self.assertEqual([v["id"] for v in iterate(c, size=200)], ["1", "2", "3"])

    def test_repeated_page_and_premature_empty_are_errors(self):
        for second in ([row(1)], []):
            c = FakeClient([{"data": [row(1)], "count": 3}, {"data": second, "count": 3}])
            with self.assertRaises(ValueError): list(iterate(c))

    def test_upstream_total_includes_duplicate_records(self):
        stats = {}
        c = FakeClient([{"data": [row(1), row(2)], "count": 3}, {"data": [row("2  ")], "count": 3}])
        self.assertEqual([v["id"] for v in iterate(c, stats=stats)], ["1", "2"])
        self.assertEqual(stats["source_count"], 3)

    def test_ids_stay_strings_and_html_is_cleaned(self):
        v = normalize(row("9007199254740993", "<em>AI</em> &amp; 芯片"))
        self.assertEqual(v["id"], "9007199254740993")
        self.assertEqual(v["title"], "AI & 芯片")
        self.assertEqual(v["year"], "2021")
        self.assertEqual(normalize(row("7397220036397056  "))["id"], "7397220036397056")

    def test_classifier_multilabel_word_boundaries(self):
        topics = json.loads(Path("config/topics.json").read_text())["topics"]
        v = classify(normalize(row(1, "FPGA加速全同态加密与大模型应用")), topics)
        self.assertTrue({"chips", "fhe", "llm"}.issubset(v["topics"]))
        unrelated = classify(normalize(row(2, "Domain and chair")), topics)
        self.assertNotIn("ai", unrelated["topics"])
        quantum = classify(normalize(row(3, "量子纠错与芯片")), topics)
        self.assertTrue({"chips", "quantum"}.issubset(quantum["topics"]))

    def test_public_allowlist_drops_credentials_and_media(self):
        v = normalize(row(1, cookie="secret", video_audio_address={"gao_definition":"private"}))
        data = {"schema_version":1,"complete":True,"count":1,"videos":[v]}
        validate(data)
        self.assertNotIn("secret", json.dumps(data))
        v["cookie"] = "secret"
        with self.assertRaises(ValueError): validate(data)

    def test_no_signed_image_urls_are_published(self):
        v = normalize(row(1)); v["cover"] = "https://example.com/cover?token=secret"
        with self.assertRaises(ValueError): validate({"schema_version":1,"complete":True,"count":1,"videos":[v]})


if __name__ == "__main__": unittest.main()
