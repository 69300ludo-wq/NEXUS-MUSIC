#!/usr/bin/env python3
import subprocess
import time
from pathlib import Path

PACKAGE = "com.nexusmusic.player"
ACTIVITY = PACKAGE + "/.MainActivity"

def adb(*args, timeout=60):
    p = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
    if p.returncode:
        raise AssertionError(f"adb {' '.join(args)} failed: {p.stderr.strip()}")
    return p.stdout

def logs(tag="NEXUS_RECOVERY:I"):
    return adb("logcat", "-d", "-s", tag)

def clear_and_stop():
    adb("shell", "am", "force-stop", PACKAGE)
    adb("logcat", "-c")
    time.sleep(0.5)

Path("test-results").mkdir(exist_ok=True)

# 1. Native MediaPlayer must really start.
clear_and_stop()
out = adb("shell", "am", "start", "-W", "-n", ACTIVITY, "--ez", "self_test_audio", "true")
assert "Status: ok" in out, "Activity launch failed"
time.sleep(3.0)
audio = logs()
assert "AUDIO_PASS" in audio, "Native MediaPlayer did not report AUDIO_PASS"
assert "PLAYER_ERROR" not in audio, "Native MediaPlayer reported an error"
print("PASS native MediaPlayer audio", flush=True)

# 2. Android native file picker must open.
clear_and_stop()
out = adb("shell", "am", "start", "-W", "-n", ACTIVITY, "--ez", "self_test_picker", "true")
assert "Status: ok" in out
time.sleep(1.5)
activities = adb("shell", "dumpsys", "activity", "activities")
assert ("documentsui" in activities.lower() or "documentsactivity" in activities.lower()),        "Android file picker did not open"
print("PASS Android file picker", flush=True)
adb("shell", "input", "keyevent", "4")

# 3. Radio Browser must return real stations from inside the app.
clear_and_stop()
out = adb("shell", "am", "start", "-W", "-n", ACTIVITY, "--ez", "self_test_radio", "true")
assert "Status: ok" in out
for _ in range(12):
    time.sleep(1.0)
    radio = logs()
    if "RADIO_DIRECTORY_PASS" in radio:
        break
assert "RADIO_DIRECTORY_PASS" in radio, "Radio directory did not return stations"
print("PASS Radio Browser inside app", flush=True)

# 4. Normal launch and crash check.
clear_and_stop()
out = adb("shell", "am", "start", "-W", "-n", ACTIVITY)
assert "Status: ok" in out
time.sleep(1.5)
with open("test-results/nexus-recovery-2-home.png", "wb") as image:
    subprocess.run(["adb", "exec-out", "screencap", "-p"],
                   stdout=image, check=True, timeout=40)

android_errors = adb("logcat", "-d", "-s", "AndroidRuntime:E")
assert "Process: " + PACKAGE not in android_errors, "NEXUS crashed"
assert "STARTED" in logs(), "Normal launch did not report STARTED"
print("PASS normal launch and no crash", flush=True)
