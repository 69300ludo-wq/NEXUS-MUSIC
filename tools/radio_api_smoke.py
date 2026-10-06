#!/usr/bin/env python3
"""Check that Radio Browser serves stations compatible with NEXUS MUSIC."""
import json
import urllib.parse
import urllib.request

ROOT = "https://de1.api.radio-browser.info"
HEADERS = {"User-Agent": "NEXUS-MUSIC-QA/0.1", "Accept": "application/json"}

def fetch(path, **filters):
    query = urllib.parse.urlencode({
        "hidebroken": "true", "order": "votes", "reverse": "true",
        "limit": "20", "is_https": "true", **filters
    })
    url = ROOT + path + "?" + query
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=20) as response:
        assert response.status == 200
        stations = json.load(response)
    assert isinstance(stations, list), "Station directory did not return a list"
    secure = [s for s in stations if str(s.get("url_resolved", s.get("url", ""))).startswith("https://")]
    assert secure, "No secure HTTPS radio stream returned"
    print("PASS", path, filters, "stations", len(stations), "HTTPS", len(secure), flush=True)

fetch("/json/stations/search", name="France")
fetch("/json/stations/search", country="France")
fetch("/json/stations/search", tag="jazz")
print("PASS Radio Browser names, countries and genres", flush=True)
