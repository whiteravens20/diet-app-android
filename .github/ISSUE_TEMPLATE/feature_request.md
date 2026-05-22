---
name: Feature Request
about: Suggest a new feature or improvement for the Diet App Android app
title: '[FEATURE] '
labels: enhancement
assignees: ''
---

> Before submitting, please review [CONTRIBUTING.md](../../CONTRIBUTING.md) for
> the project's scope and coding guidelines.

## Problem / Motivation
What problem does this solve, or what use case does it enable?

## Proposed Solution
A clear and concise description of what you want to happen.

## Alternatives Considered
Other approaches you have thought about and why you ruled them out.

## Architecture Check
The Android app is a companion client. Confirm this feature respects that:
- [ ] The backend stays the source of truth — the app does not compute
      nutrition, calorie targets or meal plans itself.
- [ ] Offline-first — the feature works, or degrades gracefully, from the Room
      cache without a network connection.
- [ ] No new API contract is needed, or the matching change is tracked in the
      [platform repo](https://github.com/whiteravens20/diet-app).
- [ ] Not applicable

## Potential Use Cases
- 
- 

## Impact on Existing Functionality
Describe whether this change might affect existing screens, the cache schema,
or the sync strategy.

## Additional Context
Screenshots, mockups, or links to prior art are welcome.
