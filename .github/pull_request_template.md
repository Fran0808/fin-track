## Description
<!-- Provide a brief description of the changes introduced by this pull request. -->

## Type of Change
<!-- Select the option that best describes this PR: -->
- [ ] `feat`: A new feature for user or system
- [ ] `fix`: A bug fix
- [ ] `refactor`: Code change that neither fixes a bug nor adds a feature
- [ ] `test`: Adding or modifying tests
- [ ] `chore`: Maintenance tasks, dependencies, build scripts
- [ ] `ci`: CI/CD workflows and automation scripts

## Modules Affected
- [ ] `api` (Java 21 / Spring Boot 3)
- [ ] `ui` (React 19 / TypeScript / Vite)
- [ ] `android` (Kotlin / Jetpack Compose)
- [ ] `scripts` / DevOps (`.github/workflows`)

## Verification Checklist
- [ ] Run `scripts/verify-all.ps1` (or local tests for affected modules)
- [ ] Backend tests passing (`mvn test` in `api/`)
- [ ] Frontend unit tests and build passing (`npm test && npm run build` in `ui/`)
- [ ] Android unit tests and build passing (`gradlew testDebugUnitTest assembleDebug` in `android/`)
- [ ] No secrets or sensitive configuration committed
