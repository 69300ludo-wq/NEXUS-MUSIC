#!/usr/bin/env python3
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.nexusmusic.player"

def adb(*args, timeout=60):
    p = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
    if p.returncode:
        raise AssertionError(f"adb {' '.join(args)} failed: {p.stderr.strip()}")
    return p.stdout

def hierarchy():
    time.sleep(1.1)
    messages = []
    for i in range(3):
        result = adb("shell", "uiautomator", "dump", "--compressed", "/sdcard/nexus-ui.xml", timeout=90)
        messages.append(result.strip())
        if "dumped to" in result:
            return ET.fromstring(adb("shell", "cat", "/sdcard/nexus-ui.xml"))
        time.sleep(2)
    raise AssertionError("UI hierarchy unavailable: " + " | ".join(messages))

def require(label):
    root = hierarchy()
    matches = [n for n in root.iter("node") if label.lower() in
               (n.get("text", "") + " " + n.get("content-desc", "")).lower()]
    if not matches:
        print("VISIBLE NODES:", [n.get("text", "") for n in root.iter("node") if n.get("text", "")][:35], flush=True)
        print("ACTIVITY:", adb("shell", "dumpsys", "activity", "activities")[-2500:], flush=True)
        print("ANDROID ERRORS:", adb("logcat", "-d", "-s", "AndroidRuntime:E")[-6000:], flush=True)
        Path("test-results").mkdir(exist_ok=True)
        with open("test-results/ui-failure.png", "wb") as out:
            subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=out, timeout=40)
        raise AssertionError("Expected visible UI text: " + label)
    print("PASS visible:", label, flush=True)
    return matches[0]

def tap(label):
    n = require(label)
    coords = list(map(int, re.findall(r"\d+", n.get("bounds", ""))))
    assert len(coords) == 4, "Invalid bounds for " + label
    x, y = (coords[0]+coords[2])//2, (coords[1]+coords[3])//2
    adb("shell", "input", "tap", str(x), str(y))
    print("PASS tapped:", label, flush=True)

Path("test-results").mkdir(exist_ok=True)
adb("logcat", "-c")
response = adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
assert "Status: ok" in response, "Activity launch failed"
time.sleep(3)

with open("test-results/nexus-home.png", "wb") as out:
    subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=out, check=True, timeout=40)
print("PASS captured home screenshot", flush=True)

require("NEXUS")
require("QUANTUM PLAYER")
tap("RADIO")
require("EXPLORER LES RADIOS DU MONDE")
require("AJOUTER UNE URL DIRECTE")
tap("AUDIO LAB")
require("PURE AUDIO")
require("ROADMAP")
tap("MUSIQUE")
require("IMPORTER UN FICHIER AUDIO")

adb("shell", "wm", "size", "480x800")
adb("shell", "wm", "density", "240")
time.sleep(1)
adb("shell", "input", "swipe", "250", "700", "250", "250", "450")
require("MEDIA3")
print("PASS compact layout scrolling", flush=True)
adb("shell", "wm", "size", "reset")
adb("shell", "wm", "density", "reset")

logs = adb("logcat", "-d", "-s", "AndroidRuntime:E")
assert "Process: " + PACKAGE not in logs, "NEXUS crashed with an AndroidRuntime exception"
print("PASS navigation, compact layout and no crash", flush=True)
