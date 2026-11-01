import os
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import build_timetable as bt


class BuildTimetableTest(unittest.TestCase):
    def test_normalize_turkish_characters(self):
        self.assertEqual(bt.normalize("İKİNDİ"), "IKINDI")
        self.assertEqual(bt.normalize("Güneş"), "GUNES")
        self.assertEqual(bt.normalize("YENİPAZAR (A)"), "YENIPAZARA")

    def test_parse_minutes(self):
        self.assertEqual(bt.parse_minutes("05:46"), 346)
        self.assertEqual(bt.parse_minutes("23:59"), 1439)
        with self.assertRaises(ValueError):
            bt.parse_minutes("24:00")
        with self.assertRaises(ValueError):
            bt.parse_minutes("5:46")

    def test_match_exact_name(self):
        provinces = {
            "ANKARA": {
                "name": "Ankara",
                "latitude": 39.9,
                "longitude": 32.8,
                "towns": [{"name": "Çankaya", "latitude": 39.8, "longitude": 32.7}],
            }
        }
        self.assertEqual(
            bt.match_coordinate("ANKARA", "ÇANKAYA", provinces, {}),
            (39.8, 32.7),
        )

    def test_match_central_district_uses_province(self):
        provinces = {
            "ANKARA": {
                "name": "Ankara",
                "latitude": 39.9,
                "longitude": 32.8,
                "towns": [],
            }
        }
        self.assertEqual(
            bt.match_coordinate("ANKARA", "ANKARA", provinces, {}),
            (39.9, 32.8),
        )

    def test_match_strips_parenthetical(self):
        provinces = {
            "AYDIN": {
                "name": "Aydın",
                "latitude": 37.8,
                "longitude": 27.8,
                "towns": [{"name": "Yenipazar", "latitude": 37.8, "longitude": 28.2}],
            }
        }
        self.assertEqual(
            bt.match_coordinate("AYDIN", "YENİPAZAR (A)", provinces, {}),
            (37.8, 28.2),
        )

    def test_override_wins(self):
        provinces = {
            "ANKARA": {
                "name": "Ankara",
                "latitude": 39.9,
                "longitude": 32.8,
                "towns": [{"name": "Çankaya", "latitude": 39.8, "longitude": 32.7}],
            }
        }
        overrides = {"ANKARA/ÇANKAYA": {"lat": 1.0, "lon": 2.0}}
        self.assertEqual(
            bt.match_coordinate("ANKARA", "ÇANKAYA", provinces, overrides),
            (1.0, 2.0),
        )

    def test_write_db_round_trip(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "t.db")
            districts = [
                {"id": 1, "name": "A", "province": "A", "lat": 39.0, "lon": 32.0}
            ]
            records = [(1, 1, 1, 346, 430, 757, 927, 1074, 1154)]
            bt.write_db(path, districts, records)
            connection = sqlite3.connect(path)
            try:
                self.assertEqual(
                    connection.execute("SELECT count(*) FROM times").fetchone()[0], 1
                )
                self.assertEqual(
                    connection.execute("SELECT imsak FROM times").fetchone()[0], 346
                )
            finally:
                connection.close()

    def test_validate_rejects_missing_days(self):
        districts = [
            {"id": 1, "name": "A", "province": "A", "lat": 39.0, "lon": 32.0}
        ]
        records = [(1, 1, 1, 346, 430, 757, 927, 1074, 1154)]
        with self.assertRaises(ValueError):
            bt.validate(districts, records)

    def test_fetch_times_uses_cache(self):
        with tempfile.TemporaryDirectory() as tmp:
            payload = {
                "data": [
                    {
                        "date": f"2026-01-{day:02d}T00:00:00.000Z",
                        "times": {
                            "imsak": "05:46",
                            "gunes": "07:10",
                            "ogle": "12:37",
                            "ikindi": "15:27",
                            "aksam": "17:54",
                            "yatsi": "19:14",
                        },
                    }
                    for day in range(1, 32)
                ]
            }
            # Only 31 rows, so this must fail the 365-row check.
            calls = []

            def fake_fetch(url):
                calls.append(url)
                return payload

            with self.assertRaises(ValueError):
                bt.fetch_times(1, tmp, fetch=fake_fetch)
            self.assertEqual(len(calls), 1)


if __name__ == "__main__":
    unittest.main()
