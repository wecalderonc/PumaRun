#!/usr/bin/env python3
"""Generate a GPX running route for the Android emulator (Extended controls > Location > Routes).

Usage: python3 tools/make_gpx.py [--km 3] [--pace 5:30] [--slow-from 1.2] [--slow-pace 7:00] > route.gpx

The route is an out-and-back line with one fix per second. --slow-from/--slow-pace inject a slow
segment starting at that distance (km) so the "below target pace" voice alert can be exercised.
"""
import argparse
import math
from datetime import datetime, timedelta, timezone

EARTH_R = 6_371_008.8


def parse_pace(text: str) -> float:
    m, s = text.split(":")
    return int(m) * 60 + int(s)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--km", type=float, default=3.0)
    ap.add_argument("--pace", default="5:30", help="min:sec per km")
    ap.add_argument("--slow-from", type=float, default=None, help="km at which to slow down")
    ap.add_argument("--slow-pace", default="7:00")
    ap.add_argument("--lat", type=float, default=19.4326)
    ap.add_argument("--lon", type=float, default=-99.1332)
    args = ap.parse_args()

    base_speed = 1000.0 / parse_pace(args.pace)
    slow_speed = 1000.0 / parse_pace(args.slow_pace)
    total_m = args.km * 1000
    half = total_m / 2

    t = datetime(2026, 1, 1, 7, 0, 0, tzinfo=timezone.utc)
    d = 0.0
    points = []
    while d <= total_m:
        along = d if d <= half else total_m - d
        lat = args.lat + math.degrees(along / EARTH_R)
        points.append((lat, args.lon, t))
        slow = args.slow_from is not None and args.slow_from * 1000 <= d < (args.slow_from + 0.4) * 1000
        d += slow_speed if slow else base_speed
        t += timedelta(seconds=1)

    print('<?xml version="1.0" encoding="UTF-8"?>')
    print('<gpx version="1.1" creator="puma make_gpx" xmlns="http://www.topografix.com/GPX/1/1">')
    print("  <trk><name>Puma test run</name><trkseg>")
    for lat, lon, ts in points:
        print(f'    <trkpt lat="{lat:.7f}" lon="{lon:.7f}"><time>{ts.strftime("%Y-%m-%dT%H:%M:%SZ")}</time></trkpt>')
    print("  </trkseg></trk>")
    print("</gpx>")


if __name__ == "__main__":
    main()
