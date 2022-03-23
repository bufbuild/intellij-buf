## Running tests

Once you have Buf CLI installed locally just run `./gredlew test` in your terminal or open the project in IntelliJ
and use *Run Tests* run configuration.

## Building a plugin

In order to run the plugin from sources either run `./gradlew runIde` in your terminal or open the project in IntelliJ
and use *Run Plugin* run configuration.

## Building a plugin

To build a local distribution of the plugin which can be shared run `./gradlew buildPlugin` from your terminal and
use `build/distributions/intellij-buf-*.zip` for sharing and [installing from disk](https://www.jetbrains.com/help/idea/managing-plugins.html#install_plugin_from_disk).
