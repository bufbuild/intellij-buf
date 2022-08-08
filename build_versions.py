#!/usr/bin/env python3

import argparse
import base64
import hashlib
import json
import os
import shutil
import sys
import time

try:
    from urllib.request import urlopen, Request, HTTPError
except ImportError:  # python 2
    from urllib2 import urlopen, Request, HTTPError

# Build list URL. From: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#runpluginverifier-task
BUILD_LIST_URL="https://jb.gg/intellij-platform-builds-list"

# This is a very crude script to just get us all the versions of IntelliJ CE.
# The use cases for getting all the versions:
# 1. Regression testing with the `runPluginVerifier` gradle task defined in build.gradle.kts`.
# 2. Getting an exhaustive list of IntelliJ versions.
if __name__ == "__main__":
    request = Request(BUILD_LIST_URL)
    request.get_method = lambda: "GET"
    response = urlopen(request)
    results = json.load(response)
    build_list = []
    for result in results:
        # IIC is IntelliJ IDEA Community Edition
        if "code" in result and result["code"] in ["IIC"]:
            for release in result["releases"]:
                if "build" not in release:
                    continue
                build = release["build"]
                split = build.split(".")
                if len(split) < 1:
                    continue
                if int(split[0]) >= 220:
                    build_list.append("\"{}-{}\"".format("IIC", build))
    print(", ".join(build_list))
