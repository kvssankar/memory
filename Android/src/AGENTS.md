# Repository Guidelines

## Project Structure & Module Organization
- Root Gradle: `settings.gradle.kts`, `build.gradle.kts`, `gradle/` wrapper.
- App module: `app/`
  - Source: `app/src/main/java/com/google/ai/edge/gallery/...`
  - UI (Jetpack Compose): `ui/*` (navigation, home, settings, chat, etc.)
  - Data & models: `data/*` (Model, Task, repositories, constants)
  - Workers: `worker/` (model download/unzip)
  - DI: `di/` (Hilt modules)
  - Resources: `app/src/main/res/`
  - Protos: `app/src/main/proto/`

## Build, Test, and Development Commands
- Build debug APK: `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`)
- Install on device: `./gradlew installDebug`
- Run unit tests: `./gradlew testDebugUnitTest`
- Run instrumentation tests: `./gradlew connectedAndroidTest` (requires device/emulator)
- Clean: `./gradlew clean`

## Coding Style & Naming Conventions
- Language: Kotlin + Jetpack Compose.
- Follow official Kotlin style (2-space indent, no tabs, no trailing whitespace).
- Names: Classes/Objects `UpperCamelCase`, functions/vars `lowerCamelCase`, constants `UPPER_SNAKE_CASE` (`const val`).
- Compose: prefer state hoisting; keep composables small and previewable; avoid business logic in UI.
- Keep changes minimal and localized; match existing patterns (e.g., add new features via `CustomTask`).

## Testing Guidelines
- Frameworks: JUnit (unit), AndroidX Test/Espresso (instrumented).
- Naming: mirror source structure; test classes end with `Test` (e.g., `ModelManagerViewModelTest`).
- Run locally with the Gradle tasks above; prefer fast unit tests first.

## Commit & Pull Request Guidelines
- Commits: concise, present-tense imperative (“Add LLM prompt lab test”). Group related changes.
- PRs: clear description, rationale, and scope. Include:
  - Affected screens/areas and before/after screenshots for UI changes.
  - Test plan (commands, devices), and risks/rollbacks.
  - Linked issues and notes on configuration changes.

## Security & Configuration Tips
- OAuth: set `clientId` and `redirectUri` in `common/ProjectConfig.kt` and update `appAuthRedirectScheme` in `app/build.gradle.kts`.
- Firebase Analytics is optional; don’t commit `google-services.json` unless intended.
- Do not commit secrets; prefer environment/local gradle properties.

## Architecture Overview (Quick)
- Model lifecycle: `ModelManagerViewModel` orchestrates allowlist, downloads (WorkManager), init/cleanup; LLM runs via MediaPipe (`LlmChatModelHelper`). Extend features by implementing `CustomTask` and binding via Hilt.

