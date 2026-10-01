#!/usr/bin/env python3
"""Refreshes web/src/main/resources/psl/icann.dat (the ICANN section of the Public Suffix List).

The list is used by HeaderParser.registrableDomain to find the organisation-level domain of a
host name (co.uk, com.au, ...). Only the ICANN section is kept: the PRIVATE section describes
hosting platforms (github.io, blogspot.com...), where the hosting company remains the sensible
"organisation" for junk filtering.
"""
import io, os, sys, urllib.request
URL = "https://raw.githubusercontent.com/publicsuffix/list/main/public_suffix_list.dat"
dst = os.path.join(os.path.dirname(__file__), "..", "web", "src", "main", "resources", "psl", "icann.dat")
text = urllib.request.urlopen(URL, timeout=60).read().decode("utf-8").split("\n")
out, inside = [], False
for line in text:
    if line.startswith("// ===BEGIN ICANN DOMAINS==="): inside = True; continue
    if line.startswith("// ===END ICANN DOMAINS==="): break
    line = line.strip()
    if inside and line and not line.startswith("//"): out.append(line.split()[0])
with io.open(dst, "w", encoding="utf-8") as f:
    f.write("# Public Suffix List, ICANN section only (https://publicsuffix.org, MPL 2.0). Regenerate with scripts/update-psl.py\n")
    f.write("\n".join(out) + "\n")
print("wrote", len(out), "rules to", os.path.normpath(dst))
