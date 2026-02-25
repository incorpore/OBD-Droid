# Contributing to OBD-Droid

Thanks for your interest in contributing! Here's how to get started.

## Getting Started

1. Fork the repository and clone your fork.
2. Initialize submodules: `git submodule update --init --recursive`
3. Open in Android Studio (Giraffe or newer) with JDK 17.
4. Build: `./gradlew :app:assembleDebug`

## Development Setup

- **Device**: Physical Android device with Bluetooth recommended for OBD testing.
- **AutoCheck API** (optional): The Vehicle History feature requires a private AutoCheck backend that you host yourself. If you have one, set your endpoint and key in `local.properties`:
  ```properties
  AUTOCHECK_API_BASE_URL=https://your-private-endpoint
  AUTOCHECK_API_KEY=your-api-key
  ```
  This backend is not included in the open-source release. All other features work without it.

## Making Changes

1. Create a feature branch from `main`.
2. Make your changes — keep commits focused and descriptive.
3. Run lint before committing: `./gradlew :app:lintDebug`
4. Verify the build: `./gradlew :app:assembleDebug`
5. Open a pull request against `main`.

## Pull Request Guidelines

- Keep PRs small and focused on a single change.
- Include a clear description of what changed and why.
- If your change affects UI, include screenshots.
- Ensure the build passes before requesting review.

## Code Style

- Java 17 source compatibility.
- Follow existing conventions in the codebase.
- No wildcard imports.
- Run the formatter before committing.

## Reporting Issues

- Use GitHub Issues to report bugs or request features.
- Include steps to reproduce, expected vs. actual behavior, and device/adapter info for OBD-related bugs.

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).
