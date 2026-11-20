#!/usr/bin/env python3
"""Writes a test-result summary to the GitHub Actions job summary (or stdout when run locally).

Usage:
  test-summary.py "Label=path/to/reports" ["Label2=path2" ...] [--coverage path/to/jacoco.csv]

Reads every JUnit-style XML file (Maven Surefire/Failsafe and Vitest) under each path, shows one row
per label and lists the failing tests. Exits with status 1 if any test failed or errored, so the
summary step can never hide a failure.
"""
import csv
import glob
import os
import sys
import xml.etree.ElementTree as ET


def read_group(path):
    """Returns (tests, failures, errors, skipped, failed_names) for all XML files under path."""
    tests = failures = errors = skipped = 0
    failed = []
    for xml_file in sorted(glob.glob(os.path.join(path, "**", "*.xml"), recursive=True)):
        try:
            root = ET.parse(xml_file).getroot()
        except ET.ParseError:
            continue
        # Surefire writes one <testsuite> per class; Vitest wraps them in a <testsuites> root.
        suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
        for suite in suites:
            if root.tag == "testsuites" and suite is not root and suite.find("testsuite") is not None:
                continue  # a container suite; its children are counted instead
            tests += int(suite.get("tests", 0))
            failures += int(suite.get("failures", 0))
            errors += int(suite.get("errors", 0))
            skipped += int(suite.get("skipped", 0))
            for case in suite.iter("testcase"):
                if case.find("failure") is not None or case.find("error") is not None:
                    failed.append(f"{case.get('classname', suite.get('name', ''))}.{case.get('name')}")
    return tests, failures, errors, skipped, failed


def coverage_line(csv_path):
    if not os.path.exists(csv_path):
        return None
    missed = covered = 0
    with open(csv_path, newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            missed += int(row["INSTRUCTION_MISSED"])
            covered += int(row["INSTRUCTION_COVERED"])
    total = missed + covered
    return f"Instruction coverage (JaCoCo, unit and integration tests together): **{covered / total:.1%}**" if total else None


def main(argv):
    coverage_path = None
    groups = []
    args = iter(argv)
    for arg in args:
        if arg == "--coverage":
            coverage_path = next(args, None)
        else:
            label, _, path = arg.partition("=")
            groups.append((label, path))

    lines = ["## Test results", "", "| Group | Tests | Failed | Errors | Skipped |", "| --- | ---: | ---: | ---: | ---: |"]
    all_failed = []
    any_bad = False
    for label, path in groups:
        tests, failures, errors, skipped, failed = read_group(path)
        any_bad = any_bad or failures > 0 or errors > 0
        lines.append(f"| {label} | {tests} | {failures} | {errors} | {skipped} |")
        all_failed += failed
    if coverage_path:
        line = coverage_line(coverage_path)
        if line:
            lines += ["", line]
    if all_failed:
        lines += ["", "### Failing tests", ""] + [f"- `{name}`" for name in all_failed]

    output = "\n".join(lines) + "\n"
    summary_file = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_file:
        with open(summary_file, "a", encoding="utf-8") as handle:
            handle.write(output)
    print(output)
    return 1 if any_bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
