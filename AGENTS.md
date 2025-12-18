# Repository Guidelines

## Project Structure & Module Organization
- Module: `app/` (Gradle subproject). Entry point: `sirsim.App`.
- Source: `app/src/main/java/sirsim/...` with packages: `simulation`, `network`, `percolation`, `utils`.
- Tests: `app/src/test/java`; resources: `app/src/test/resources`.
- Example assets: `app/src/main/java/sirsim/network/files/` (CSV/TXT for demos).
- Build output: `app/build/`; experiment outputs and logs: `out/`.
- Notebooks: exploratory `notebooks/`; ad‑hoc checks `test-notebooks/`.

## Build, Test, and Development Commands
- Build: `./gradlew build` — compiles with Java 21 toolchain pinned by wrapper.
- Test: `./gradlew test` — JUnit 5; report at `app/build/reports/tests/`.
- Run (K‑core): `./gradlew :app:run --args='kcore --n 2000 --z 6 --k 3 --steps 41 --trials 10 --seed 1 --out out/kcore/2000/results.csv'`.
- Run FastSAR: `./gradlew :app:runFastSAR` (macOS helper: `./run-fastsar.sh`).
- Quick no‑Gradle run:
  ```bash
  mkdir -p app/build/classes && find app/src/main/java -name "*.java" > app/build/sources.list
  javac -d app/build/classes @app/build/sources.list
  java -cp app/build/classes sirsim.App <subcommand> [options]
  ```

## Coding Style & Naming Conventions
- Java 21; indent 4 spaces; target 100–120 columns.
- Naming: packages lowercase (`sirsim.*`); classes `PascalCase`; methods/fields `camelCase`; constants `UPPER_SNAKE_CASE`.
- Prefer immutability (`final`), explicit nullability, and small, focused methods.
- Keep CLI flag patterns consistent with `App.java`; update README when flags change.

## Testing Guidelines
- Framework: JUnit Jupiter 5.
- Location: tests in `app/src/test/java`; resources in `app/src/test/resources`.
- Naming: `*Test.java` (e.g., `KCoreTest`).
- Determinism: use `--seed` flags or inject `java.util.Random` to ensure reproducibility.

## Commit & Pull Request Guidelines
- Commits: Conventional Commits (e.g., `feat(network): add FastSAR runner`).
- PRs: include purpose/context, CLI examples run, expected outputs under `out/` (paths + sample CSVs), and plots/screenshots if relevant. Link issues; keep changes focused; update docs/examples when behavior or flags change.
- Do not commit large artifacts in `out/`. Use `notebooks/` for exploration.

## Security & Configuration Tips
- No global JDK setup required; Gradle wrapper manages toolchains.
- Avoid hard‑coding absolute paths; prefer project‑relative paths.
- When adding new CLI options, ensure `--help` remains coherent and examples under `app/src/main/java/sirsim/...` stay in sync.
