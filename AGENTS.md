# Repository Guidelines

## Project Structure & Module Organization
- Module: `app/` (Gradle subproject). Entry point: `sirsim.App`.
- Source: `app/src/main/java/sirsim/...` with packages: `simulation`, `network`, `percolation`, `utils`.
- Example assets: `app/src/main/java/sirsim/network/files/` (CSV/TXT for demos).
- Build output: `app/build/`; experiment outputs and logs: `out/`.
- Notebooks: exploration in `notebooks/` and ad‑hoc checks in `test-notebooks/`.

## Build, Test, and Development Commands
- Build: `./gradlew build` — compiles with the Java 21 toolchain pinned by the wrapper.
- Run (K‑core): `./gradlew :app:run --args='kcore --n 2000 --z 6 --k 3 --steps 41 --trials 10 --seed 1 --out out/kcore/2000/results.csv'`.
- Run FastSAR: `./gradlew :app:runFastSAR` (or `./run-fastsar.sh` on macOS to prevent sleep).
- Tests: `./gradlew test` — JUnit 5; report at `app/build/reports/tests/`.
- Quick no‑Gradle run:
  ```bash
  mkdir -p app/build/classes && find app/src/main/java -name "*.java" > app/build/sources.list
  javac -d app/build/classes @app/build/sources.list
  java -cp app/build/classes sirsim.App <subcommand> [options]
  ```

## Coding Style & Naming Conventions
- Language: Java 21; indent 4 spaces; target 100–120 columns.
- Naming: packages lowercase (`sirsim.*`); classes `PascalCase`; methods/fields `camelCase`; constants `UPPER_SNAKE_CASE`.
- Prefer immutability (`final`), explicit nullability, and small, focused methods.
- Keep CLI flag patterns consistent with `App.java`; update README when flags change.

## Testing Guidelines
- Framework: JUnit Jupiter 5.
- Location: tests in `app/src/test/java`; resources in `app/src/test/resources`.
- Naming: `*Test.java` (e.g., `KCoreTest`). Test public APIs and deterministic behavior.
- Randomness: use `--seed` or inject `Random` to ensure reproducibility.

## Commit & Pull Request Guidelines
- Commits: Conventional Commits (`feat:`, `fix:`, `chore:`, `refactor:`), optional scopes (e.g., `feat(network): ...`).
- PRs: include purpose/context, CLI examples run, expected outputs under `out/` (paths and sample CSVs), and plots/screens if relevant. Link issues, keep changes focused, and update docs/examples when behavior or flags change.
- Do not commit large artifacts in `out/`. Use `notebooks/` for exploration.

## Notes & Tips
- Gradle wrapper manages toolchains; no global JDK setup required.
- Keep experiment outputs under `out/` and out of version control.
