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
    time.sleep(0.5)
    for _ in range(5):
        out = adb("shell", "uiautomator", "dump", "--compressed",
                  "/sdcard/nexus-ui.xml", timeout=90)
        if "dumped to" in out:
            return ET.fromstring(adb("shell", "cat", "/sdcard/nexus-ui.xml"))
        time.sleep(1)
    raise AssertionError("UI hierarchy unavailable")

def find(label):
    root = hierarchy()
    return [n for n in root.iter("node")
            if label.lower() in (n.get("text","") + " " + n.get("content-desc","")).lower()]

def require(label):
    matches = find(label)
    if matches:
        print("PASS visible:", label, flush=True)
        return matches[0]
    with open("test-results/ui-failure.png", "wb") as out:
        subprocess.run(["adb","exec-out","screencap","-p"], stdout=out, timeout=40)
    raise AssertionError("Expected visible UI text: " + label)

def tap(label):
    node = require(label)
    nums = list(map(int, re.findall(r"\d+", node.get("bounds",""))))
    assert len(nums) == 4
    x=(nums[0]+nums[2])//2
    y=(nums[1]+nums[3])//2
    adb("shell","input","tap",str(x),str(y))
    print("PASS tapped:", label, flush=True)

def swipe_up():
    adb("shell","input","swipe","540","1800","540","700","350")
    time.sleep(0.6)

Path("test-results").mkdir(exist_ok=True)
adb("logcat","-c")

launch = adb("shell","am","start","-W","-n",PACKAGE+"/.MainActivity")
assert "Status: ok" in launch
time.sleep(1.5)

with open("test-results/nexus-recovery-home.png","wb") as out:
    subprocess.run(["adb","exec-out","screencap","-p"],stdout=out,check=True,timeout=40)

require("NEXUS MUSIC")
require("CORE RECOVERY 2.0")
require("TEST AUDIO INTERNE")
require("CHOISIR UNE MUSIQUE")

tap("TEST AUDIO INTERNE")
time.sleep(2.5)
logs = adb("logcat","-d","-s","NEXUS_NATIVE_AUDIO:I")
assert "PLAYING NEXUS AUDIO TEST" in logs, "Native MediaPlayer never entered PLAYING"
print("PASS native MediaPlayer entered PLAYING", flush=True)

require("LECTURE ACTIVE")

tap("CHOISIR UNE MUSIQUE")
time.sleep(1.2)
activities = adb("shell","dumpsys","activity","activities")
assert ("documentsui" in activities.lower() or "documentsactivity" in activities.lower())
print("PASS Android audio file picker opened", flush=True)
adb("shell","input","keyevent","4")
time.sleep(0.8)

swipe_up()
require("RECHERCHER UNE RADIO")
require("URL RADIO DIRECTE")

tap("RECHERCHER UNE RADIO")
require("Rechercher une radio")
tap("Station")
time.sleep(2.5)
require("Radios disponibles")
print("PASS Radio Browser directory displayed", flush=True)
adb("shell","input","keyevent","4")
time.sleep(0.8)

tap("URL RADIO DIRECTE")
require("URL radio directe")
print("PASS direct radio URL dialog opened", flush=True)
adb("shell","input","keyevent","4")

android_errors = adb("logcat","-d","-s","AndroidRuntime:E")
assert "Process: " + PACKAGE not in android_errors
print("PASS core recovery navigation and no crash", flush=True)

with open("test-results/nexus-recovery-final.png","wb") as out:
    subprocess.run(["adb","exec-out","screencap","-p"],stdout=out,check=True,timeout=40)
