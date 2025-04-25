# Changelog

## [Unreleased]

## [0.6.0] - 2025-04-25

- Update Buf logo by @pkwarren in https://github.com/bufbuild/intellij-buf/pull/310
- Fix linting in IntelliJ plugin by @pkwarren in https://github.com/bufbuild/intellij-buf/pull/322

## [0.5.0] - 2025-02-07

- Support 2025 EAP builds.
- Update to v2 gradle IntelliJ plugin by @pkwarren in https://github.com/bufbuild/intellij-buf/pull/279

## [0.4.2] - 2024-11-27

- Update to buf v1.32.0 by @pkwarren in https://github.com/bufbuild/intellij-buf/pull/229
- Avoid changing file contents if format failed by @pkwarren in https://github.com/bufbuild/intellij-buf/pull/274

## [0.4.1] - 2024-05-18

- Fix compatilbility issue with latest EAP by @pkwarren in https://github.com/bufbuild/intellij-buf/pull/222

## [0.4.0] - 2024-05-16

- Updates the plugin to work with the newly released Buf CLI [v1.32.0](https://github.com/bufbuild/buf/releases/tag/v1.32.0), including v2 config files and support for v2 multi-module workspaces.
- Improved support for BSR module external libraries.
- Support v2 buf.lock files and v3 cached modules by @pkwarren in https://github.com/bufbuild/intellij-buf/pull/206
- Create index of buf.yaml for v2 workspaces by @pkwarren in https://github.com/bufbuild/intellij-buf/pull/207
- Update ignore quick fix for v2 buf.yaml by @pkwarren in https://github.com/bufbuild/intellij-buf/pull/212
- Reduce scope to only search project files by @pkwarren in https://github.com/bufbuild/intellij-buf/pull/213
- Implement support for refreshing proto roots by @pkwarren in https://github.com/bufbuild/intellij-buf/pull/214
- Fix logging of expected exit codes by @pkwarren in https://github.com/bufbuild/intellij-buf/pull/216
- Run buf build on startup to prime cache by @pkwarren in https://github.com/bufbuild/intellij-buf/pull/217
- Allow module keys with ports in full name by @pkwarren in https://github.com/bufbuild/intellij-buf/pull/219
- Filter out files in excluded directories by @pkwarren in https://github.com/bufbuild/intellij-buf/pull/220

## [0.3.1] - 2024-02-12

- Preserve newlines with buf format by @pkwarren in https://github.com/bufbuild/intellij-buf/pull/181

## [0.3.0] - 2024-01-23

- Support Protobuf plugin by kanro by @devkanro in https://github.com/bufbuild/intellij-buf/pull/166
- @devkanro made their first contribution in https://github.com/bufbuild/intellij-buf/pull/166

## [0.2.1] - 2023-08-18

- Migrate to Kotlin UI DSL v2 in https://github.com/bufbuild/intellij-buf/pull/138
- Remove deprecated fileTypeFactory in https://github.com/bufbuild/intellij-buf/pull/139

## [0.2.0] - 2023-08-18

- Reduce auto-save impact for lint/breaking checks in https://github.com/bufbuild/intellij-buf/pull/136
- Update plugin to require 2022.3 or later in https://github.com/bufbuild/intellij-buf/pull/135

## [0.1.5] - 2023-07-28

- Improve process handling with the buf CLI.
- Add displayName attribute to fix error in IntelliJ 2023.2.

## [0.1.4] - 2023-05-17

### Features

- Support configuring the path to the Buf CLI.
- Add support for resolving dependencies from the Buf CLI v2 module cache.

## [0.1.0]

Initial beta release.

## [0.1.1]

Update to support IntelliJ Platform 221.*

## [0.1.2]

Update to support IntelliJ Platform 222.*

[Unreleased]: https://github.com/bufbuild/intellij-buf/compare/v0.6.0...HEAD
[0.6.0]: https://github.com/bufbuild/intellij-buf/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/bufbuild/intellij-buf/compare/v0.4.2...v0.5.0
[0.4.2]: https://github.com/bufbuild/intellij-buf/compare/v0.4.1...v0.4.2
[0.4.1]: https://github.com/bufbuild/intellij-buf/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/bufbuild/intellij-buf/compare/v0.3.1...v0.4.0
[0.3.1]: https://github.com/bufbuild/intellij-buf/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/bufbuild/intellij-buf/compare/v0.2.1...v0.3.0
[0.2.1]: https://github.com/bufbuild/intellij-buf/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/bufbuild/intellij-buf/compare/v0.1.5...v0.2.0
[0.1.5]: https://github.com/bufbuild/intellij-buf/compare/v0.1.4...v0.1.5
[0.1.4]: https://github.com/bufbuild/intellij-buf/compare/v0.1.0...v0.1.4
[0.1.2]: https://github.com/bufbuild/intellij-buf/commits/v0.1.2
[0.1.1]: https://github.com/bufbuild/intellij-buf/compare/v0.1.2...v0.1.1
[0.1.0]: https://github.com/bufbuild/intellij-buf/compare/v0.1.1...v0.1.0
