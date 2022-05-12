# Buf Support for IntelliJ Platform

![Build](https://github.com/bufbuild/intellij-buf/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/com.github.bufbuild.intellij.svg)](https://plugins.jetbrains.com/plugin/com.github.bufbuild.intellij)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/com.github.bufbuild.intellij.svg)](https://plugins.jetbrains.com/plugin/com.github.bufbuild.intellij)

<!-- Plugin description -->
This plugin extends Protocol Buffer support in the IDE by integrating with [Buf](https://buf.build).

Buf CLI provides advanced linting and detection of potentially breaking changes in your Proto messages and services.
Integration with Buf Schema Registry helps manage, discover and share API definitions.
<!-- Plugin description end -->

## Development Guide

Please submit bug reports and feature requests via GitHub Issues and don't hesitate to contribute via PRs.

## Running tests

Once you have Buf CLI installed locally just run `./gredlew test` in your terminal or open the project in IntelliJ
and use *Run Tests* run configuration.

## Running the plugin

In order to run the plugin from sources either run `./gradlew runIde` in your terminal or open the project in IntelliJ
and use *Run Plugin* run configuration.

## Distributing the plugin

To build a local distribution of the plugin which can be shared run `./gradlew buildPlugin` from your terminal and
use `build/distributions/intellij-buf-*.zip` for sharing and [installing from disk](https://www.jetbrains.com/help/idea/managing-plugins.html#install_plugin_from_disk).

## Publishing the plugin

Process of publishing the plugin to JetBrains Marketplace is automated via GitHub Releases:
    
* Update `pluginVersion` in `gradle.properties`.
* Add a new entry to `CHANGELOG.md` for the new version.
* Create a GitHub Release which will automatically submit the plugin for a review.
* After the review, which can take a day or two, the plugin will be available on JetBrains Marketplace.
