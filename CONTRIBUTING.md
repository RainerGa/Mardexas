# Contributing to RichMarkDownEditor (Mardexas)

Thank you for considering a contribution to RichMarkDownEditor! This project is
licensed under the GNU General Public License v3.0 (GPL-3.0).

## Getting started

1. Fork the repository on GitHub.
2. Clone your fork recursively (includes the `rich-text-area` submodule):
   ```sh
   git clone --recurse-submodules https://github.com/<your-fork>/Mardexas.git
   cd Mardexas
   ```
3. Install JDK 25 and Maven.
4. Build and run:
   ```sh
   ./editor/start.sh
   ```
5. Run the test suite:
   ```sh
   mvn test
   ```

## Making changes

1. Create a feature branch from `main`:
   ```sh
   git checkout -b feature/your-feature-name
   ```
2. Make your changes. All Java source files must carry the GPL-3.0 copyright
   header (see existing files for the format).
3. Add or update unit tests in `editor/src/test/java/` as appropriate.
4. Run the full build and test suite:
   ```sh
   mvn clean verify
   ```
5. Commit with a clear message and push to your fork.
6. Open a pull request against the `main` branch.

## Code style

- Follow the existing Java code conventions in the project.
- Every `.java` file must start with the GPL-3.0 copyright header block.
- Keep methods small and focused; add Javadoc for public APIs.
- UI code runs on the JavaFX Application Thread; use `Platform.runLater` where
  needed.

## License

By contributing, you agree that your contributions will be licensed under the
GPL-3.0 license, as published in the `LICENSE` file.