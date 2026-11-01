#!/usr/bin/env python3
"""Build the bundled Diyanet timetable database."""

import argparse
import calendar
import datetime as dt
import hashlib
import json
import os
import re
import sqlite3
import time
import unicodedata
import urllib.error
import urllib.request

CANONICAL_YEAR = 2026
BASE_DISTRICTS = "https://ezanvakti.emushaf.net"
BASE_TIMES = "https://ezanvakti.imsakiyem.com"
COORDS_URL = (
    "https://raw.githubusercontent.com/BuNickTamYirmiHarfli/"
    "turkey-cities-districts-json/main/cities.json"
)
USER_AGENT = "vakit-timetable-generator/1.0"
PRAYERS = ("imsak", "gunes", "ogle", "ikindi", "aksam", "yatsi")
LAT_MIN, LON_MIN, LAT_MAX, LON_MAX = 35.8, 25.6, 42.2, 44.9

SCHEMA = """
CREATE TABLE districts (
  id INTEGER PRIMARY KEY,
  name TEXT NOT NULL,
  province TEXT NOT NULL,
  lat REAL NOT NULL,
  lon REAL NOT NULL
) WITHOUT ROWID;

CREATE TABLE times (
  district_id INTEGER NOT NULL,
  month INTEGER NOT NULL,
  day INTEGER NOT NULL,
  imsak INTEGER NOT NULL,
  gunes INTEGER NOT NULL,
  ogle INTEGER NOT NULL,
  ikindi INTEGER NOT NULL,
  aksam INTEGER NOT NULL,
  yatsi INTEGER NOT NULL,
  PRIMARY KEY (district_id, month, day)
) WITHOUT ROWID;
"""


def normalize(value):
    value = value.replace("İ", "I").replace("ı", "i").upper()
    value = unicodedata.normalize("NFKD", value)
    value = "".join(ch for ch in value if not unicodedata.combining(ch))
    return re.sub(r"[^A-Z0-9]", "", value)


def parse_minutes(value):
    if not re.fullmatch(r"\d{2}:\d{2}", value):
        raise ValueError(f"bad time: {value!r}")
    hours, minutes = (int(part) for part in value.split(":"))
    if hours > 23 or minutes > 59:
        raise ValueError(f"time out of range: {value!r}")
    return hours * 60 + minutes


def match_coordinate(province, district, provinces, overrides):
    key = f"{province}/{district}"
    if key in overrides:
        value = overrides[key]
        if value == "province":
            return _province_coordinate(province, provinces)
        return float(value["lat"]), float(value["lon"])

    province_row = provinces.get(normalize(province))
    if province_row is None:
        return None

    towns = {normalize(town["name"]): town for town in province_row["towns"]}
    candidate = towns.get(normalize(district))
    if candidate is None:
        stripped = re.sub(r"\s*\(.*\)\s*$", "", district)
        candidate = towns.get(normalize(stripped))
    if candidate is not None:
        return float(candidate["latitude"]), float(candidate["longitude"])

    if normalize(district) == normalize(province):
        return float(province_row["latitude"]), float(province_row["longitude"])
    return None


def _province_coordinate(province, provinces):
    row = provinces.get(normalize(province))
    if row is None:
        return None
    return float(row["latitude"]), float(row["longitude"])


def get_json(url, attempts=5):
    for attempt in range(attempts):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code in (429, 500, 502, 503, 504) and attempt < attempts - 1:
                time.sleep(2 ** attempt)
                continue
            raise
        except (urllib.error.URLError, TimeoutError):
            if attempt < attempts - 1:
                time.sleep(2 ** attempt)
                continue
            raise
    raise RuntimeError("unreachable")


def fetch_times(district_id, cache_dir, fetch=get_json):
    cache_path = os.path.join(cache_dir, f"{district_id}.json")
    if os.path.exists(cache_path):
        with open(cache_path, encoding="utf-8") as handle:
            payload = json.load(handle)
    else:
        start = f"{CANONICAL_YEAR}-01-01"
        end = f"{CANONICAL_YEAR}-12-31"
        url = (
            f"{BASE_TIMES}/api/prayer-times/{district_id}/range"
            f"?startDate={start}&endDate={end}"
        )
        payload = fetch(url)
        os.makedirs(cache_dir, exist_ok=True)
        with open(cache_path, "w", encoding="utf-8") as handle:
            json.dump(payload, handle)

    rows = payload.get("data", [])
    if len(rows) != 365:
        raise ValueError(f"district {district_id}: expected 365 rows, got {len(rows)}")
    return rows


def build_records(districts, times_by_district):
    records = []
    for district in districts:
        for row in times_by_district[district["id"]]:
            date = dt.date.fromisoformat(row["date"][:10])
            minutes = [parse_minutes(row["times"][prayer]) for prayer in PRAYERS]
            records.append((district["id"], date.month, date.day, *minutes))
    return records


def validate(districts, records):
    counts = {}
    keys = set()
    for record in records:
        district_id = record[0]
        counts[district_id] = counts.get(district_id, 0) + 1
        key = (record[0], record[1], record[2])
        if key in keys:
            raise ValueError(f"duplicate row: {key}")
        keys.add(key)
        for value in record[3:]:
            if not 0 <= value <= 1439:
                raise ValueError(f"minute out of range: {value}")

    for district in districts:
        if counts.get(district["id"], 0) != 365:
            raise ValueError(
                f"district {district['id']} has {counts.get(district['id'], 0)} rows"
            )
        if not (LAT_MIN <= district["lat"] <= LAT_MAX):
            raise ValueError(f"latitude out of Turkey: {district}")
        if not (LON_MIN <= district["lon"] <= LON_MAX):
            raise ValueError(f"longitude out of Turkey: {district}")


def write_db(path, districts, records):
    directory = os.path.dirname(path)
    if directory:
        os.makedirs(directory, exist_ok=True)
    if os.path.exists(path):
        os.remove(path)
    connection = sqlite3.connect(path)
    try:
        connection.executescript(SCHEMA)
        connection.executemany(
            "INSERT INTO districts (id, name, province, lat, lon) VALUES (?, ?, ?, ?, ?)",
            [
                (d["id"], d["name"], d["province"], d["lat"], d["lon"])
                for d in districts
            ],
        )
        connection.executemany(
            "INSERT INTO times "
            "(district_id, month, day, imsak, gunes, ogle, ikindi, aksam, yatsi) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            records,
        )
        connection.commit()
    finally:
        connection.close()


def write_version(db_path, districts, records):
    with open(db_path, "rb") as handle:
        digest = hashlib.sha256(handle.read()).hexdigest()[:16]
    version = (
        f"v1;year={CANONICAL_YEAR};districts={len(districts)};"
        f"rows={len(records)};sha256={digest}"
    )
    version_path = os.path.join(os.path.dirname(db_path), "timetable.version")
    with open(version_path, "w", encoding="utf-8") as handle:
        handle.write(version + "\n")
    return version


def write_fixture(out_path):
    districts = [
        {
            "id": 9206,
            "name": "ANKARA",
            "province": "ANKARA",
            "lat": 39.9334,
            "lon": 32.8597,
        },
        {
            "id": 9541,
            "name": "İSTANBUL",
            "province": "İSTANBUL",
            "lat": 41.0082,
            "lon": 28.9784,
        },
    ]
    records = []
    for district in districts:
        for month in range(1, 13):
            for day in range(1, calendar.monthrange(CANONICAL_YEAR, month)[1] + 1):
                records.append(
                    (district["id"], month, day, 346, 430, 757, 927, 1074, 1154)
                )
    validate(districts, records)
    write_db(out_path, districts, records)
    version = write_version(out_path, districts, records)
    print(f"fixture: {len(districts)} districts, {len(records)} rows, {version}")


def fetch_catalog():
    provinces = get_json(f"{BASE_DISTRICTS}/sehirler/2")
    districts = []
    for province in provinces:
        rows = get_json(f"{BASE_DISTRICTS}/ilceler/{province['SehirID']}")
        for row in rows:
            districts.append(
                {
                    "id": int(row["IlceID"]),
                    "name": row["IlceAdi"],
                    "province": province["SehirAdi"],
                }
            )
        time.sleep(1.2)
    return provinces, districts


def load_overrides(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def load_coordinates(path, url=COORDS_URL):
    if not os.path.exists(path):
        payload = get_json(url)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(payload, handle)
        return payload
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="app/src/main/assets/timetable.db")
    parser.add_argument("--coords", default="scripts/cities.json")
    parser.add_argument("--overrides", default="scripts/district-overrides.json")
    parser.add_argument("--cache-dir", default="scripts/cache")
    parser.add_argument("--fixture", action="store_true")
    parser.add_argument("--report-unmatched", action="store_true")
    args = parser.parse_args(argv)

    if args.fixture:
        write_fixture(args.out)
        return 0

    coordinates = load_coordinates(args.coords)
    provinces = {normalize(row["name"]): row for row in coordinates}
    overrides = load_overrides(args.overrides)

    api_provinces, catalog = fetch_catalog()
    unmatched = []
    matched = []
    for district in catalog:
        coordinate = match_coordinate(
            district["province"], district["name"], provinces, overrides
        )
        if coordinate is None:
            unmatched.append(district)
        else:
            district["lat"], district["lon"] = coordinate
            matched.append(district)

    if unmatched:
        print(f"unmatched: {len(unmatched)}")
        for district in unmatched:
            print(f"  {district['province']}/{district['name']} ({district['id']})")
        return 0 if args.report_unmatched else 1

    print(f"matched: {len(matched)} districts, {len(api_provinces)} provinces")
    if args.report_unmatched:
        return 0
    if len(api_provinces) != 81:
        raise SystemExit(f"expected 81 provinces, got {len(api_provinces)}")
    if len(matched) < 850:
        raise SystemExit(f"expected at least 850 districts, got {len(matched)}")

    times_by_district = {}
    for index, district in enumerate(matched, start=1):
        times_by_district[district["id"]] = fetch_times(district["id"], args.cache_dir)
        if index % 25 == 0:
            print(f"  fetched {index}/{len(matched)}")
        time.sleep(1.2)

    records = build_records(matched, times_by_district)
    validate(matched, records)
    write_db(args.out, matched, records)
    version = write_version(args.out, matched, records)
    print(f"wrote {args.out}: {len(matched)} districts, {len(records)} rows")
    print(version)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
