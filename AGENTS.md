# Repository Guidelines

## Project Structure & Modules
- `app/src/main/java`: Java sources (Java 21). Packages under `sirsim.*` (e.g., `network`, `simulation`, `percolation`, `utils`).
- `app/src/main/resources`: Runtime resources.
- `app/src/test/java`: Place JUnit tests here (none committed yet).
- `out/`: Simulation CSV outputs (gitignored).
- `build/`, `app/build/`: Gradle artifacts (gitignored).
- `notebooks/`, `test-notebooks/`: Experiments and scratch work (gitignored).
- Entrypoints: `sirsim.App` (basic demo), `sirsim.FastSIR`, `sirsim.FastSAR`.

## Build, Test, and Run
- Build: `./gradlew build` — compiles and runs tests.
- Run demo: `./gradlew :app:run` — runs `sirsim.App`.
- Run FastSAR: `./gradlew :app:runFastSAR` or `./run-fastsar.sh` (macOS prevents sleep via `caffeinate`).
- Quick no-Gradle compile/run:
  - `mkdir -p app/build/classes && find app/src/main/java -name "*.java" > app/build/sources.list`
  - `javac -d app/build/classes @app/build/sources.list`
  - `java -cp app/build/classes sirsim.App`

## Coding Style & Naming
- Language: Java 21; 4-space indentation; braces K&R style.
- Packages: `lower.snake` under `sirsim`; classes: `PascalCase`; methods/fields: `camelCase`.
- Prefer immutability where reasonable; use `final` for constants.
- No linter configured; follow standard Java conventions. Keep files small and cohesive.

## Testing Guidelines
- Framework: JUnit Jupiter (JUnit 5).
- Location: `app/src/test/java/...` mirroring main packages.
- Naming: `ClassNameTest.java` with descriptive `@Test` methods.
- Run: `./gradlew test`. Coverage not enforced; consider JaCoCo if adding metrics.

## Commit & Pull Requests
- Conventional commits: `feat:`, `fix:`, `chore:`, optional scope (e.g., `feat(network): RR graph`).
- Commits: imperative mood, concise subject, include context in body if needed.
- PRs: clear description, linked issues, CLI/command examples, performance notes, and sample outputs (avoid committing files in `out/`).
- CI/build should pass locally (`./gradlew build`) before requesting review.

## Architecture Notes
- Graphs: `sirsim.network` (+ `topology`: ER/BA/RR). Simulators: `sirsim.simulation` (SIR/SAR). K-core: `sirsim.percolation`.
- Utilities: `sirsim.utils` (`Logger`, `Array`, `PathsEx`). CSVs write under `out/...` using helper paths.

