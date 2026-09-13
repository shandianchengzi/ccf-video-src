"""Stage an allowlisted Pages artifact, with integrity hashes and source config."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
from urllib.parse import urlsplit
from catalog import validate, write_json


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="https://shandianchengzi.github.io/ccf-video-src")
    args = parser.parse_args()
    base = args.base_url.rstrip("/")
    uri = urlsplit(base)
    if uri.scheme != "https" or not uri.hostname or uri.username or uri.query or uri.fragment:
        raise ValueError("Invalid public base URL")
    catalog = json.loads(Path("build/catalog.json").read_text(encoding="utf-8"))
    validate(catalog)
    jar = Path("build/ccf.jar").read_bytes()
    out = Path("dist"); out.mkdir(exist_ok=True)
    # Explicit allowlist; never copy the workspace or an account file.
    for name in ("index.html", "app.js", "style.css"):
        shutil.copyfile(Path("site") / name, out / name)
    (out / ".nojekyll").touch()
    (out / "ccf.jar").write_bytes(jar)
    config = {"spider": base + "/ccf.jar;md5;" + hashlib.md5(jar).hexdigest(), "sites": [
        {"key": "ccf", "name": "CCF数字图书馆", "type": 3, "api": "csp_CCF",
         "searchable": 1, "quickSearch": 0, "filterable": 1, "ext": {"catalog": base + "/catalog.json"}}
    ]}
    write_json(out / "tvbox.json", config)
    write_json(out / "config.json", config)
    write_json(out / "catalog.json", catalog)
    manifest = {"generated_at": catalog["generated_at"], "count": catalog["count"],
                "series_complete": catalog["series_complete"], "subscription": base + "/tvbox.json",
                "topic_counts": {t["id"]: sum(t["id"] in v["topics"] for v in catalog["videos"]) for t in catalog["topics"]},
                "sha256": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(out.iterdir()) if p.is_file() and p.name != "manifest.json"}}
    write_json(out / "manifest.json", manifest)
    print("Pages artifact:", catalog["count"], "videos;", len(jar), "byte dex JAR")


if __name__ == "__main__": main()
