#!/usr/bin/env python3

import argparse
import base64
import hashlib
import json
import os
import re
import shutil
import sys
import time

from urllib.request import urlopen, Request, HTTPError

# Build list URL. From: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#runpluginverifier-task
BUILD_LIST_URL="https://jb.gg/intellij-platform-builds-list"
SUPPORTED_TEST_CODES = ["IIC", "IIU"]
MIN_MAJOR_VERSION = 213

def parse_json(response):
    results = json.load(response)
    version_map = {}
    for result in results:
        # IIC is IntelliJ IDEA Community Edition
        if "code" not in result or result["code"] not in SUPPORTED_TEST_CODES:
            continue
        for release in result["releases"]:
            if "build" not in release:
                continue
            build = release["build"]
            split = build.split(".")
            if len(split) < 1:
                continue
            major_version = int(split[0])
            # This is the `pluginSinceBuild` value in `build.gradle.kts`
            if major_version < MIN_MAJOR_VERSION:
                continue
            id = "{}-{}".format(result["code"], build)
            if major_version not in version_map:
                version_map[major_version] = [id]
            version_map[major_version].append(id)
    # Sort results just to be certain of ordering
    for key in version_map:
        version_map[key].sort()
    return version_map

# This is a very crude script to just get us the most recent versions of IntelliJ.
# The use cases for getting all the versions:
# 1. Regression testing with the `runPluginVerifier` gradle task defined in `build.gradle.kts`.
# 2. Getting an exhaustive list of IntelliJ versions.
#
# This does override the existing gradle/test-intellij.versions.toml file since that is what
# gradle uses as the source of truth for the verification plugin. This would ideally only be
# run and checked in on CI.
if __name__ == "__main__":
    request = Request(BUILD_LIST_URL)
    request.get_method = lambda: "GET"
    response = urlopen(request)
    version_map = parse_json(response)
    test_versions = []
    for key in version_map:
        list = version_map[key]
        if len(list) == 0:
            continue
        latest_version = list[-1]
        test_versions.append(latest_version)
    versions_toml_content = ""
    previous_versions_toml_content = ""
    with open("./gradle/test-intellij.versions.toml", 'r') as f:
        previous_versions_toml_content = f.read()
        versions_string = ", ".join(test_versions)
        versions_toml_content = re.sub('versionList = \"(.*)\"\n', 'versionList = "{}"'.format(versions_string), previous_versions_toml_content)
        f.close()
    with open("./gradle/test-intellij.versions.toml", 'w') as f:
        f.write(versions_toml_content)
        f.close()

    if versions_toml_content != previous_versions_toml_content:
        print("Overwrote test-intellij.versions.toml.")
        print("Previous version:")
        print(previous_versions_toml_content)
        print("Testing version:")
        print(versions_toml_content)
