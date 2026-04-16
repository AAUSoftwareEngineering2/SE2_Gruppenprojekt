# Project Conventions

This document defines the technical and workflow conventions for the Kotlin Android implementation of Kuhhandel.

Adherence to these standards is required for all contributors.

## 1. Commit Message Convention
Commit messages must follow the format:
`<type>[optional scope]: <description>`
`[optional body]`
`[optional footer(s)]`

- Do NOT use past tense ("feat: added feature..."), instead phrase your commit messages like a directive ("feat: add feature...")

### Types
- **feat**: A new feature.
- **fix**: A bug fix.
- **config**: Build system, dependencies, or environment changes.
- **docs**: Documentation only.

### Examples
- `feat(lang): add Polish language`
- `fix: prevent racing of requests`
- `config: update mailmap file`

---

## 2. Branch Naming Convention
All branch names must be lowercase and hyphen-separated.

### Structural Rules
- **Character Set**: Use only alphanumeric characters (`a-z`, `0-9`) and hyphens (`-`).
- **Separation**: Use single hyphens. No continuous hyphens (`--`) or trailing hyphens.
- **Format**: `<type>/<task-id>-<description>`

### Valid Types
- `feat/`
- `bugfix/`
- `config/`
- `docs/`

### Examples
- `feat/T-456-user-authentication`
- `bugfix/T-789-fix-header-styling`

---

## 3. Pull Request (PR) Convention

### Workflow
- **Direction**: PRs must target the `main` branch from your specific feature or fix branch. Merges from a staging branch are not permitted.
- **Content**: Every PR requires a clear title and a description that summarizes all included commits. If applicable, use screenshots to display UI changes.
- **Self-Review**: Ensure all debug logs and "TODO" comments are removed or converted to tracked issues before submission.

### Documentation Requirements
- **Summary**: A bulleted list of changes.
- **Context**: Explain the rationale behind the implementation if it deviates from standard patterns.
- **Issue Linking**: Reference relevant ticket IDs (e.g., "Closes #T-123").

## 4. Drawable Naming Convention
Android does not support subdirectories within the `res/drawable` directory. To maintain organization and prevent naming collisions, the following prefixes must be used:

| Prefix | Category | Example               |
| :--- | :--- |:----------------------|
| `ic_` | Icons | `ic_settings.xml`     |
| `ig_` | Miscellaneous in-game sprites | `ig_card_back.xml`    |
| `mm_` | Main menu assets | `mm_play_button.xml`  |
| `bg_` | Backgrounds | `bg_pasture_main.xml` |
| `auc_` | Auction animals | `auc_cow.xml`         |
| `hs_` | Animal headshots | `hs_pig.xml`          |
| `hso_` | Animal headshot with outline | `hso_pig.xml`         |
| `fa_` | Farms | `fa_stable.xml`       |

---
