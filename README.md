# Buf Support for IntelliJ Platform

![Build](https://github.com/bufbuild/intellij-buf/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/com.github.bufbuild.intellij.svg)](https://plugins.jetbrains.com/plugin/com.github.bufbuild.intellij)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/com.github.bufbuild.intellij.svg)](https://plugins.jetbrains.com/plugin/com.github.bufbuild.intellij)

<!-- Plugin description -->
This plugin extends Protocol Buffer support in the IDE by integrating with [Buf](https://buf.build).
It provides fast diagnostics, go-to-definition, code completion, hover documentation, and find references
through the [Buf Language Server](https://buf.build/docs/cli/editors-lsp/)
(requires buf CLI v1.59.0+ and IntelliJ 2025.1+).
The plugin automatically falls back to CLI-based diagnostics when the language server is unavailable or if using an older version of buf.

The plugin also supports code formatting with `buf format`,
background linting with `buf lint`,
breaking change detection with `buf breaking`,
and integration with Buf Schema Registry to help manage, discover and share API definitions.
Configure features in **Settings → Tools → Buf**.
<!-- Plugin description end -->

## Development Guide

Please submit bug reports and feature requests via GitHub Issues and don't hesitate to contribute via PRs.

## Running tests

Once you have Buf CLI installed locally just run `./gradlew test` in your terminal or open the project in IntelliJ
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
