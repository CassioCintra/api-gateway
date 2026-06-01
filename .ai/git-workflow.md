# Git Workflow Rules

## Branch Strategy

All development happens in:

development

Never:
- commit directly to main
- push directly to main

main only receives code through Pull Requests from development.

## Commit Authorization

Always ask permission before creating commits.

Before committing:
- present proposed commit messages
- group files by logical context

## Commit Format

Use Conventional Commits:

type(scope): description

Examples:
- feat(feature-flags): add create/update/delete
- fix(persistence): handle null entity

## Commit Organization

Production code and tests belong in the same commit.

Flyway migrations related to persistence adapters must be committed together with:
- adapter
- tests

## Pull Requests

Before suggesting a PR:
- ensure tests pass
- ensure architecture rules are respected
- ensure no unrelated refactoring was introduced

## Minimal Changes

Edit only necessary code.

Avoid:
- unrelated refactors
- formatting-only commits
- architectural rewrites unless requested

## Transparency

If something is ambiguous:
- ask before acting

For complex tasks:
- present execution plan first
- wait for approval

Always explain:
- root cause of bugs
- reasoning behind major decisions

## Destructive Actions

Always require explicit confirmation before:
- deleting files
- overwriting data
- resetting databases
- force pushing
- rewriting git history

## Final Summary

After multiple changes:
- summarize modified files
- explain why each file changed
- mention executed tests
