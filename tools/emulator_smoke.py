#!/usr/bin/env python3
import subprocess
import time
from pathlib import Path

PACKAGE="com.nexusmusic.player"
ACTIVITY=PACKAGE+"/.MainActivity"

def adb(*args,timeout=60):
    p=subprocess.run(["adb",*args],capture_output=True,text=True,timeout=timeout)
    if p.returncode:
        raise AssertionError(f"adb {' '.join(args)} failed: {p.stderr.strip()}")
    return p.stdout

def clear():
    adb("shell","am","force-stop",PACKAGE)
    adb("logcat","-c")
    time.sleep(0.4)

def logs():
    return adb("logcat","-d","-s","NEXUS_SAFE:V")

Path("test-results").mkdir(exist_ok=True)

# GitHub's Android runner has no reliable host audio sink, so speaker/audio
# output is intentionally NOT asserted here.

clear()
out=adb("shell","am","start","-W","-n",ACTIVITY,"--ez","self_test_picker","true")
assert "Status: ok" in out
time.sleep(1.2)
activities=adb("shell","dumpsys","activity","activities")
assert ("documentsui" in activities.lower() or "documentsactivity" in activities.lower()), "File picker did not open"
print("PASS Android file picker",flush=True)
adb("shell","input","keyevent","4")

clear()
out=adb("shell","am","start","-W","-n",ACTIVITY,"--ez","self_test_radio","true")
assert "Status: ok" in out
radio=""
for _ in range(60):
    time.sleep(1)
    radio=logs()
    if "RADIO_PASS" in radio:
        break
if "RADIO_PASS" not in radio:
    print("RADIO LOGS:", radio, flush=True)
assert "RADIO_PASS" in radio, "Radio Browser did not return stations"
print("PASS in-app radio directory",flush=True)

clear()
out=adb("shell","am","start","-W","-n",ACTIVITY)
assert "Status: ok" in out
time.sleep(1)
with open("test-results/nexus-safe-mode-home.png","wb") as image:
    subprocess.run(["adb","exec-out","screencap","-p"],stdout=image,check=True,timeout=40)
crash=adb("logcat","-d","-s","AndroidRuntime:E")
assert "Process: "+PACKAGE not in crash
print("PASS normal launch and no crash",flush=True)
