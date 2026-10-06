#!/usr/bin/env python3
import subprocess
import time
from pathlib import Path

PACKAGE="com.nexusmusic.finalplayer"
ACTIVITY=PACKAGE+"/com.nexusmusic.player.MainActivity"

def adb(*args,timeout=60):
    p=subprocess.run(["adb",*args],capture_output=True,text=True,timeout=timeout)
    if p.returncode:
        raise AssertionError(f"adb {' '.join(args)} failed: {p.stderr.strip()}")
    return p.stdout

def clear():
    adb("shell","am","force-stop",PACKAGE)
    adb("logcat","-c")
    time.sleep(0.4)

Path("test-results").mkdir(exist_ok=True)

# 1. Clean launch.
clear()
out=adb("shell","am","start","-W","-n",ACTIVITY)
assert "Status: ok" in out
time.sleep(1)
crash=adb("logcat","-d","-s","AndroidRuntime:E")
assert "Process: "+PACKAGE not in crash
print("PASS clean launch",flush=True)

# 2. Native Android file picker.
clear()
out=adb("shell","am","start","-W","-n",ACTIVITY,"--ez","self_test_picker","true")
assert "Status: ok" in out
time.sleep(1.5)
activities=adb("shell","dumpsys","activity","activities")
assert ("documentsui" in activities.lower() or "documentsactivity" in activities.lower()), "File picker did not open"
print("PASS Android file picker",flush=True)
adb("shell","input","keyevent","4")

# 3. Radio search from inside the app.
clear()
out=adb("shell","am","start","-W","-n",ACTIVITY,"--ez","self_test_radio","true")
assert "Status: ok" in out
radio=""
for _ in range(60):
    time.sleep(1)
    radio=adb("logcat","-d","-s","NEXUS_FINAL:V")
    if "RADIO_PASS" in radio:
        break
assert "RADIO_PASS" in radio, "In-app radio search did not return stations"
print("PASS in-app radio search",flush=True)

# 4. Final screenshot and crash check.
clear()
out=adb("shell","am","start","-W","-n",ACTIVITY)
assert "Status: ok" in out
time.sleep(1)
with open("test-results/nexus-3-final-home.png","wb") as image:
    subprocess.run(["adb","exec-out","screencap","-p"],stdout=image,check=True,timeout=40)
crash=adb("logcat","-d","-s","AndroidRuntime:E")
assert "Process: "+PACKAGE not in crash
print("PASS NEXUS MUSIC 3.0 final smoke",flush=True)
