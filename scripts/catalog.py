"""Public CCF metadata only. No credentials, details, media URLs or video downloads."""
import argparse
import datetime as dt
import html
import json
import os
from pathlib import Path
import re
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "https://dl.ccf.org.cn"
FIELDS = {"id", "title", "cover", "date", "year", "views", "access", "series", "topics", "matches"}


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(value, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    os.replace(tmp, path)


def clean(value):
    return html.unescape(re.sub(r"<[^>]*>", "", str(value or ""))).strip()


def normalize(row):
    ident = str(row.get("id", "")).strip()
    title = clean(row.get("title"))
    if not re.fullmatch(r"[0-9]+", ident) or not title:
        raise ValueError("CCF list row has no valid id/title: " + repr((ident, title[:100])))
    stamp = int(row.get("date") or 0)
    year = str(dt.datetime.fromtimestamp(stamp / 1000, dt.timezone(dt.timedelta(hours=8))).year) if stamp else ""
    cover = str(row.get("cover") or "")
    if cover.startswith("/upload/"):
        cover = BASE + "/file_server" + cover
    elif cover.startswith("/"):
        cover = urllib.parse.urljoin(BASE, cover)
    if urllib.parse.urlsplit(cover).scheme not in ("https", "http"):
        cover = ""
    return dict(id=ident, title=title, cover=cover, date=stamp, year=year,
                views=int(row.get("view_count") or 0), access=clean(row.get("visible_tag")),
                series=[], topics=[], matches={})


def classify(video, topics):
    text = video["title"] + " " + " ".join(video["series"])
    matches = {}
    for topic in topics:
        found = []
        for word in topic["keywords"]:
            pattern = re.escape(word)
            # ASCII abbreviations must not match inside another English word (AI != domain).
            if word.isascii():
                pattern = r"(?<![A-Za-z0-9])" + pattern + r"(?![A-Za-z0-9])"
            if re.search(pattern, text, re.I):
                found.append(word)
        if found:
            matches[topic["id"]] = found
    video["topics"] = list(matches)
    video["matches"] = matches
    return video


class Client:
    def __init__(self, delay=0.6):
        self.delay = delay

    def post(self, path, data):
        for attempt in range(4):
            time.sleep(self.delay + (2 ** attempt if attempt else 0))
            req = urllib.request.Request(BASE + path, data=urllib.parse.urlencode(data).encode(), headers={
                "User-Agent": "CCF-Personal-Catalog/1.0 (+GitHub Actions; public metadata only)",
                "Referer": BASE + "/video/videoIndex.html?_ack=1",
                "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            })
            try:
                with urllib.request.urlopen(req, timeout=40) as response:
                    result = json.load(response)
                if result.get("success") is not True or not isinstance(result.get("data"), dict):
                    raise ValueError("CCF returned an unsuccessful or changed response")
                return result["data"]
            except urllib.error.HTTPError as exc:
                if exc.code not in (408, 429, 500, 502, 503, 504) or attempt == 3:
                    raise RuntimeError("CCF HTTP " + str(exc.code)) from None
            except (urllib.error.URLError, TimeoutError, json.JSONDecodeError):
                if attempt == 3:
                    raise RuntimeError("CCF unavailable or returned invalid JSON") from None
        raise RuntimeError("CCF retry budget exhausted")


def iterate(client, series="", size=200, max_pages=1000):
    seen = set()
    first_count = None
    for page in range(1, max_pages + 1):
        data = client.post("/video/getVideoList", dict(pageNum=page, pageSize=size, searchTerm="",
                           dataYear="", seriesText=series, sortRule="date"))
        rows, count = data.get("data"), data.get("count")
        if not isinstance(rows, list) or not isinstance(count, int) or count < 0:
            raise ValueError("CCF pagination schema changed")
        if first_count is None:
            first_count = count
        if not rows:
            if len(seen) < min(first_count, count):
                raise ValueError("CCF returned an empty page before the advertised total")
            return
        new = 0
        for row in rows:
            video = normalize(row)
            if video["id"] not in seen:
                seen.add(video["id"])
                new += 1
                yield video
        if not new:
            raise ValueError("CCF repeated a page; refusing an incomplete catalog")
        if len(seen) >= count:
            return
        if page % 10 == 0:
            print("Pages:", series or "all", page, "records:", len(seen), "/", count, flush=True)
        # Never infer completion from len(rows) < requested size; server may cap it.
    raise ValueError("CCF catalog exceeded the page budget")


def crawl(client, topics, with_series=True):
    conditions = client.post("/video/getConditions", {})
    for key in ("dateYears", "meetingSeries"):
        if not isinstance(conditions.get(key), list) or not conditions[key]:
            raise ValueError("CCF conditions are missing")
    videos = {v["id"]: v for v in iterate(client)}
    if not videos:
        raise ValueError("Refusing an empty catalog")
    print("Public catalog:", len(videos), "videos", flush=True)
    if with_series:
        for series in dict.fromkeys(conditions["meetingSeries"]):
            count = 0
            for v in iterate(client, series):
                if v["id"] in videos:
                    videos[v["id"]]["series"].append(series)
                    count += 1
            print("Series:", series, count, flush=True)
    items = [classify(v, topics) for v in videos.values()]
    items.sort(key=lambda v: (-v["date"], v["id"]))
    return dict(schema_version=1, generated_at=dt.datetime.now(dt.timezone.utc).isoformat(),
                source=BASE + "/video/videoIndex.html", complete=True, series_complete=with_series,
                count=len(items), conditions=conditions, topics=topics, videos=items)


def validate(catalog):
    if catalog.get("schema_version") != 1 or not catalog.get("complete"):
        raise ValueError("Not a complete supported catalog")
    videos = catalog["videos"]
    if not videos or catalog["count"] != len(videos):
        raise ValueError("Catalog count mismatch")
    ids = [v["id"] for v in videos]
    if len(set(ids)) != len(ids):
        raise ValueError("Duplicate video ids")
    for video in videos:
        if set(video) != FIELDS:
            raise ValueError("Unexpected public metadata fields")
        for url in [video["cover"]]:
            if urllib.parse.urlsplit(url).query:
                raise ValueError("Do not publish signed/query-bearing URLs")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="build/catalog.json")
    parser.add_argument("--topics", default="config/topics.json")
    parser.add_argument("--previous")
    parser.add_argument("--skip-series", action="store_true", help="Development only")
    args = parser.parse_args()
    topics = json.loads(Path(args.topics).read_text(encoding="utf-8"))["topics"]
    catalog = crawl(Client(), topics, not args.skip_series)
    validate(catalog)
    if args.previous and Path(args.previous).exists():
        previous = json.loads(Path(args.previous).read_text(encoding="utf-8"))
        if catalog["count"] < previous["count"] * 0.9:
            raise ValueError("Catalog shrank by more than 10%; keeping previous deployment")
    write_json(args.output, catalog)
    print("Saved", catalog["count"], "public records", flush=True)


if __name__ == "__main__":
    main()
