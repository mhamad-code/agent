# Agent

A native Android chat client for coding agents, built with Kotlin and Jetpack Compose.

## Build

Requirements: JDK 17, Android SDK with compileSdk 35, and Gradle 8.9 (use the wrapper).

```properties
# local.properties
sdk.dir=/path/to/Android/sdk
```

```bash
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # JVM unit tests
./gradlew :app:lintDebug            # Android lint
```

The release build reads its signing settings from environment variables, so no key is
stored in this repository:

```bash
export AGENT_KEYSTORE=/path/to/agent-release.jks
export AGENT_KEYSTORE_PASSWORD=...
export AGENT_KEY_ALIAS=agent
export AGENT_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

## Layout

```
app/src/main/java/com/agent/ai/
├── chat/        chat screen, composer, message list, view model
├── data/chat/   Room entities, message tree, attachments, budget, export
├── markdown/    Markdown parser, inline styling, syntax highlighting
├── providers/   provider interface and the Kilo gateway client
├── ui/          theme, shared components, screens
└── updates/     update check, download and install
```

`app/schemas/` holds the exported Room schemas used by the migration test.

## Updates

`update.json` in the repository root is what the app reads to find a new version. Its
`apk.url` must point at a file that is downloadable without signing in.

## License

No license has been chosen yet, so nobody may reuse this code.
