#!/usr/bin/env python3
"""Require executed instrumentation tests, even when Gradle reports success."""

import sys
from pathlib import Path
from xml.etree import ElementTree

directory = Path(sys.argv[1] if len(sys.argv) > 1 else
                 "charts-compose/build/outputs/androidTest-results/connected/debug")
reports = sorted(directory.rglob("TEST-*.xml"))
if not reports:
    raise SystemExit("No instrumentation test XML: the device runner did not complete.")

total = skipped = 0
for report in reports:
    root = ElementTree.parse(report).getroot()
    for suite in root.iter("testsuite"):
        cases = suite.findall("testcase")
        if int(suite.get("tests", "0")) != len(cases):
            raise SystemExit(f"Incomplete instrumentation report: {report}")
        if int(suite.get("failures", "0")) or int(suite.get("errors", "0")):
            raise SystemExit(f"Instrumentation failed: {report}")
        if int(suite.get("skipped", "0")) != sum(case.find("skipped") is not None for case in cases):
            raise SystemExit(f"Incomplete instrumentation report: {report}")
        for case in cases:
            if case.find("failure") is not None or case.find("error") is not None:
                raise SystemExit(f"Instrumentation failed: {case.get('name')}")
            total += 1
            skipped += case.find("skipped") is not None

if total == skipped:
    raise SystemExit("No instrumentation tests executed.")
print(f"Device results: {total - skipped} passed, {skipped} skipped.")
