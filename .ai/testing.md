# Testing and TDD Rules

Follow mandatory TDD cycle:

1. Red
2. Green
3. Refactor

## Red Phase

Before production code:
- write the test first
- ensure the test compiles
- ensure the test fails

## Green Phase

Implement only the minimum code necessary to pass.

Avoid premature optimization.

## Refactor Phase

Improve code without changing behavior.

All tests must remain green.

## Service Tests

Service tests must use:
- JUnit 5
- MockitoExtension
- @Mock
- @InjectMocks
- AssertJ

Never start Spring context for application layer tests.

## Integration Tests

Persistence and web adapters must use:
- Testcontainers
- real infrastructure

Never use:
- H2
- in-memory database replacements

## Test Naming

Use:

should{Behavior}When{Condition}()

Examples:
- shouldThrowWhenFlagAlreadyExists()
- shouldCreateFlagDisabledAndPublishEvent()

## Package Structure

Test package must mirror production package.

Example:

cassio.featureflags.application.FeatureFlagServiceTest

tests:

cassio.featureflags.application.FeatureFlagService

## Commit Consistency

Production code and tests must be committed together.

Never commit:
- implementation without test
- test without implementation

## Verification

Before proposing a commit:
- run all available tests
- run mvn verify
- confirm no regressions
