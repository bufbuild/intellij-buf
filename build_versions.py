#!/usr/bin/env python3

import argparse
import base64
import hashlib
import json
import os
import shutil
import sys
import time

from urllib.request import urlopen, Request, HTTPError

# Build list URL. From: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#runpluginverifier-task
BUILD_LIST_URL="https://jb.gg/intellij-platform-builds-list"
MIN_MAJOR_VERSION = 213

def parse_json(response):
    results = json.load(response)
    version_map = {}
    for result in results:
        # IIC is IntelliJ IDEA Community Edition
        if "code" not in result or result["code"] not in ["IIC"]:
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
            id = "{}-{}".format("IIC", build)
            if major_version not in version_map:
                version_map[major_version] = [id]
            version_map[major_version].append(id)
    # Sort results just to be certain of ordering
    for key in version_map:
        version_map[key].sort()
    return version_map

# This is a very crude script to just get us all the versions of IntelliJ CE.
# The use cases for getting all the versions:
# 1. Regression testing with the `runPluginVerifier` gradle task defined in `build.gradle.kts`.
# 2. Getting an exhaustive list of IntelliJ versions.
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
    print(",".join(test_versions))