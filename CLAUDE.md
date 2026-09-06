# Grid

Android library for tabular views (`:grid`) plus a demo app (`:demo`). Plain Java on Android
views and Material Components (Material Design 2) — no Kotlin, no Compose.

## Language

**Everything inside the repository is written in American English**: comments, Javadoc, README, all
class, method, field, parameter, local variable and test method names, string resources and
commit messages.

## Conventions

- Minimum Android API 24, built with AGP 8.13 / Gradle 8.14 / JDK 17.
- One commit per stage; commit messages in English, without umlauts.
- Comments explain *why*, not *what* — the code already says what it does.
- Unit tests run on the JVM (`gradlew :grid:testDebugUnitTest`). Anything that touches
  Android views cannot be covered there; keep layout arithmetic in `GridLayoutMath` /
  `GridMetrics` so it stays testable.

## Build and test

```
gradlew :demo:assembleDebug      # build the demo
gradlew :demo:installDebug       # install on device/emulator
gradlew :grid:testDebugUnitTest  # unit tests of the library
gradlew build                    # everything, including lint
```

`JAVA_HOME` needs to point at a JDK 17, e.g. the one shipped with Android Studio.
