#!/usr/bin/env python3
"""Feed a simulated run into a running emulator, one `adb emu geo fix` per second.

Usage: python3 tools/feed_gps.py --km 1.2 --pace 5:30 [--slow-from 0.45 --slow-to 0.75 --slow-pace 7:00]

Runs due north from --lat/--lon. Unlike make_gpx.py this needs no emulator UI, so it can be scripted.
"""
import argparse
import math
import os
import subprocess
import time

EARTH_R = 6_371_008.8
ADB = os.environ.get("ADB", "adb")


def parse_pace(text: str) -> float:
    m, s = text.split(":")
    return int(m) * 60 + int(s)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--km", type=float, required=True, help="distance at which to stop")
    ap.add_argument("--start-km", type=float, default=0.0, help="continue a previous feed from here")
    ap.add_argument("--pace", default="5:30")
    ap.add_argument("--slow-from", type=float)
    ap.add_argument("--slow-to", type=float)
    ap.add_argument("--slow-pace", default="7:00")
    ap.add_argument("--lat", type=float, default=19.4326)
    ap.add_argument("--lon", type=float, default=-99.1332)
    args = ap.parse_args()

    speed = 1000.0 / parse_pace(args.pace)
    slow_speed = 1000.0 / parse_pace(args.slow_pace)
    d = args.start_km * 1000
    start = time.monotonic()
    tick = 0
    while d <= args.km * 1000:
        lat = args.lat + math.degrees(d / EARTH_R)
        slow = args.slow_from is not None and args.slow_from * 1000 <= d < (args.slow_to or 1e9) * 1000
        v = slow_speed if slow else speed
        knots = v * 1.943844
        subprocess.run([ADB, "emu", "geo", "fix", f"{args.lon:.7f}", f"{lat:.7f}", "2240", "12", f"{knots:.2f}"],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if tick % 30 == 0:
            print(f"t={tick:4d}s d={d:7.1f} m speed={v:.2f} m/s", flush=True)
        d += v
        tick += 1
        time.sleep(max(0.0, start + tick - time.monotonic()))
    # The emulator dead-reckons from the last reported velocity, so park the runner explicitly.
    lat = args.lat + math.degrees(d / EARTH_R)
    subprocess.run([ADB, "emu", "geo", "fix", f"{args.lon:.7f}", f"{lat:.7f}", "2240", "12", "0"],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print(f"done: {d:.0f} m in {tick} s", flush=True)


if __name__ == "__main__":
    main()
