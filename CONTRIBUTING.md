# Contributing

This is a fork of Client for Jira 3.9.0 (ALM Works), maintained to bring the
client up to date with the current Jira Cloud REST API. See
[About this fork](README.md#about-this-fork) for scope and method.

## Branches

`cloud` is the trunk of this fork. Work lands there.

The upstream project used `cloud` and `server` for two API variants; this fork
only follows `cloud`. Do not build `master`.

## Commits

Use [Conventional Commits](https://www.conventionalcommits.org/), with a scope
naming the **functional area** rather than the Ant module:

```
type(scope): short description in the imperative, lower case

Body explaining what changed and why, wrapped at 80 columns.
```

Scopes in use: `wizard`, `sync`, `build`, `docs`, `launcher`.

Common types: `feat`, `fix`, `docs`, `build`, `chore`, `refactor`, `test`.

Examples:

```
feat(wizard): add copy-messages button to connection wizard
fix(sync): migrate issue search to enhanced search endpoint (api/2/search/jql, CHANGE-2046)
docs(readme): add fork maintainer, scope and AI-assisted method notes
```

Note that the upstream history does not follow this convention. It starts with
the first commit of this fork.

## Building

You need **Oracle JDK 8, update 112 to 202**, and Apache Ant.

The upper bound is not cosmetic. 8u202 is the last update that ships JavaFX
(`jre/lib/ext/jfxrt.jar`), and the application launcher requires
`javafx.application.Platform` on its first line. It is also the last Oracle
build before the licence change. OpenJDK and Java 9+ do not work.

```sh
cp ant/build.sh.example ant/build.sh   # fill in ANT_HOME and JDK8_HOME
cd ant
./build.sh
```

`ant/build.sh` is deliberately untracked, because it holds machine-local paths.
`ant/build.sh.example` is the template and documents the two Windows-specific
details: quote the paths and write them in Windows form with forward slashes,
and keep `-Dfile.encoding=UTF-8`, without which the build fails on a non-UTF-8
locale.

The build writes to `build/.dist/jiraclient/` and produces
`build/.dist/jiraclient-NNNN.zip`.

## Running what you built

The launcher picks a runtime from `JAVA_HOME` first, then a `jre/` bundled in
the distribution, then whatever `javaw.exe` is on the `PATH` — with no version
check. If a recent Java 8 JRE is on the `PATH` the application starts against a
runtime without JavaFX and dies silently, because `jiraclient.bat` uses
`javaw.exe` and detaches.

Use `bin/jiraclient-local.bat`, which pins a JavaFX-capable JDK for that process
only. Override the path with the `JIRACLIENT_JAVA_HOME` environment variable.

## Before you commit

* Build cleanly: `ALL.compile` at minimum, the full build with tests when you
  touch anything under `*/src`.
* `ReferredByQueryTests` fails in the full test run and passes in isolation.
  That is a known pre-existing issue, not something you introduced. See
  [docs/CHANGES-FORK.md](docs/CHANGES-FORK.md).
* Record anything a future reader would need in
  [docs/CHANGES-FORK.md](docs/CHANGES-FORK.md).

## Licence

GPL v3, as the upstream project. Attribution to ALM Works, Inc. stays intact.
