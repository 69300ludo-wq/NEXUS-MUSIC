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
    time.sleep(0.8)
    messages = []
    for _ in range(4):
        result = adb("shell", "uiautomator", "dump", "--compressed", "/sdcard/nexus-ui.xml", timeout=90)
        messages.append(result.strip())
        if "dumped to" in result:
            return ET.fromstring(adb("shell", "cat", "/sdcard/nexus-ui.xml"))
        time.sleep(1.5)
    raise AssertionError("UI hierarchy unavailable: " + " | ".join(messages))

def find(label):
    root = hierarchy()
    return [n for n in root.iter("node") if label.lower() in
            (n.get("text", "") + " " + n.get("content-desc", "")).lower()]

def require(label):
    matches = find(label)
    if not matches:
        root = hierarchy()
        print("VISIBLE NODES:", [n.get("text", "") for n in root.iter("node") if n.get("text", "")][:45], flush=True)
        print("ACTIVITY:", adb("shell", "dumpsys", "activity", "activities")[-3000:], flush=True)
        print("ANDROID ERRORS:", adb("logcat", "-d", "-s", "AndroidRuntime:E")[-6000:], flush=True)
        Path("test-results").mkdir(exist_ok=True)
        with open("test-results/ui-failure.png", "wb") as out:
            subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=out, timeout=40)
        raise AssertionError("Expected visible UI text: " + label)
    print("PASS visible:", label, flush=True)
    return matches[0]

def wait_require(label, attempts=8):
    for _ in range(attempts):
        matches = find(label)
        if matches:
            print("PASS visible:", label, flush=True)
            return matches[0]
        time.sleep(0.8)
    return require(label)

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
time.sleep(2)

with open("test-results/nexus-home.png", "wb") as out:
    subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=out, check=True, timeout=40)
print("PASS captured home screenshot", flush=True)

require("NEXUS")
require("QUANTUM PLAYER")
require("IMPORTER UN FICHIER AUDIO")
require("TEST AUDIO INTERNE")

tap("TEST AUDIO INTERNE")
wait_require("NEXUS Audio Test")
wait_require("LECTURE ACTIVE")
print("PASS ExoPlayer entered active playback state", flush=True)

tap("IMPORTER UN FICHIER AUDIO")
time.sleep(1.5)
activities = adb("shell", "dumpsys", "activity", "activities")
assert ("documentsui" in activities.lower() or "documentsactivity" in activities.lower()), "Android file picker did not open"
print("PASS Android audio file picker opened", flush=True)
adb("shell", "input", "keyevent", "4")
time.sleep(0.8)

tap("RADIO")
require("EXPLORER LES RADIOS DU MONDE")
require("AJOUTER UNE URL DIRECTE")

tap("EXPLORER LES RADIOS DU MONDE")
require("Explorer les radios du monde")
tap("Station")
wait_require("Radios disponibles")
print("PASS Radio Browser directory displayed", flush=True)
adb("shell", "input", "keyevent", "4")
time.sleep(0.8)

tap("AJOUTER UNE URL DIRECTE")
require("Ajouter une radio")
print("PASS direct radio URL dialog opened", flush=True)
adb("shell", "input", "keyevent", "4")
time.sleep(0.8)

tap("AUDIO LAB")
require("PURE AUDIO")
require("ROADMAP")

tap("MUSIQUE")
require("IMPORTER UN FICHIER AUDIO")

logs = adb("logcat", "-d", "-s", "AndroidRuntime:E")
assert "Process: " + PACKAGE not in logs, "NEXUS crashed with an AndroidRuntime exception"
print("PASS navigation, audio self-test, file picker, radio directory and no crash", flush=True)

with open("test-results/nexus-stable-home.png", "wb") as out:
    subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=out, check=True, timeout=40)
