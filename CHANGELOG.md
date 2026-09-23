# Changelog

All notable changes to this fork are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Versions up to 3.9.0 were produced by ALM Works, Inc.; upstream development
stopped in May 2020 and 3.9.0 was never released. Numbering resumes at 3.10.0
with the first release of this fork. See [About this fork](README.md#about-this-fork).

## [Unreleased]

## [3.10.0] - 2026-09-23

First release of the fork. It restores synchronisation with Jira Cloud, which
no version of the client could still perform.

### Fixed

- **Issue search migrated to the enhanced search endpoint** (`api/2/search/jql`,
  Atlassian CHANGE-2046). Atlassian retired `/rest/api/*/search`, which answers
  **410 Gone** on Jira Cloud, so every earlier version fails to synchronise
  outright. Paging moved from a `startAt` offset to a `nextPageToken` cursor.
- **False upload conflicts.** Editing any field of an issue whose previous
  upload had succeeded reported *"This issue has conflicting changes on the
  server"*. The cause was rich text fields being read as null; landing the
  migration on API v2 rather than v3 removed it, with no change to the conflict
  logic itself.
- **Build no longer compiles.** `PROGRESS_LOAD_NEXT` was narrowed to a single
  argument without updating every call site, leaving the `cloud` branch broken.

### Added

- **Copy Messages button** in the connection wizard, so diagnostics can be
  copied out of a failing connection attempt.
- `bin/jiraclient-local.bat`, which pins a JavaFX-capable JDK for its own
  process. The stock launcher takes the first `javaw.exe` on the `PATH` with no
  version check, and a runtime without JavaFX makes the application die
  silently.

### Changed

- **Corrected the licence text shipped for iText.** `license/iText-license.txt`
  carried the Mozilla Public License 1.1, which describes iText 2.x. The
  version actually redistributed is 5.4.4, licensed under the **GNU Affero
  General Public License v3** — iText moved to the AGPL with 5.0.0 in 2009, and
  the jar's manifest says so. The AGPL and the GPL v3 explicitly permit
  combination with one another, so nothing about the product's licensing
  changes; the file simply named the wrong licence for a component that ships.
- The progress readout during a query load shows the number of issues loaded
  instead of a percentage. The enhanced search endpoint no longer returns a
  total result count, so no percentage can be computed.
- Source encoding is pinned to UTF-8 at build time. Without it the build fails
  on a machine whose platform encoding is not UTF-8.

### Known issues

- **Comment visibility groups are not loaded.** `LoadCommentVisibility` issues
  an unbounded JQL query, which the enhanced search endpoint rejects.
- **`ReferredByQueryTests` fails in the full test run** and passes in isolation.
  The module shares one JVM across its test classes, and SQLite runs out of
  temporary storage by the time this class runs. Pre-existing, not a regression.
- **`DateUtilTests` fails on a JVM with recent timezone data.** The test walks
  every zone the JVM knows, so zones added after the test was written break it:
  `Pacific/Kanton`, introduced in 2021, is off by one day number. It passes on
  Oracle 8u202 (2019 tzdata) and fails on Zulu 8u504, which is what CI runs.
  The test already excludes one other zone by hand for the same reason.

### Compatibility

- Requires **Oracle JDK 8, update 112 to 202**. 8u202 is the last update
  shipping JavaFX, which the launcher requires. OpenJDK and Java 9+ do not work.
- Jira Cloud only. The Server/Data Center variant is not covered by this fork.
- The local workspace (`~/.JIRAClient/`) carries over from 3.9.0 unchanged.

[Unreleased]: https://github.com/lucabonazzi-ai/JiraDesktopClient/compare/v3.10.0...HEAD
[3.10.0]: https://github.com/lucabonazzi-ai/JiraDesktopClient/releases/tag/v3.10.0
