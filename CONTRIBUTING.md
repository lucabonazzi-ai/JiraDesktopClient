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

You need **a Java 8 JDK that ships JavaFX**, and Apache Ant.

What actually matters is `jre/lib/ext/jfxrt.jar`: the application launcher
requires `javafx.application.Platform` on its first line. Java 9+ does not work.

Two runtimes are known to build the project:

* **Oracle JDK 8, update 112 to 202.** 8u202 is the last update that ships
  JavaFX, and the last Oracle build before the licence change.
* **An OpenJDK 8 build with the JavaFX bundle**, such as Zulu 8 `jdk+fx`. This
  contradicts the older note that OpenJDK does not work; that note held for
  OpenJDK builds *without* JavaFX. The CI builds on Zulu 8 `jdk+fx`, because
  the Oracle JDK cannot be downloaded unattended.

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
* Add an entry under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md) for
  anything a user would notice. The two files have different jobs:
  `CHANGES-FORK.md` is the engineering record, `CHANGELOG.md` is what ships.

## Releasing

Versions follow [Semantic Versioning](https://semver.org/). The product version
lives in `ant/jiraclient.properties`; the build number is separate and is set by
CI from the run number.

1. Set `product.version` and `product.fileVersion` in
   `ant/jiraclient.properties` (`3.10.0` and `3_10_0` respectively).
2. Move the `## [Unreleased]` entries in `CHANGELOG.md` under the new version,
   dated, and add the comparison links at the bottom.
3. Update `Version:` and the change history in
   `env/distimage.jiraclient/RELEASE.txt`, and the top block of
   `env/distimage.jiraclient/etc/welcome.html`, which is what a user sees first.
4. Commit, then tag and push:

   ```sh
   git tag -a v3.10.0 -m "Client for Jira 3.10.0"
   git push origin cloud --follow-tags
   ```

The tag triggers `.github/workflows/release.yml`, which builds the distribution
and attaches the ZIP to a **draft** release. Review it, then publish. The
workflow refuses to run if the tag and `product.version` disagree.

Built ZIPs are never committed. Git does not deduplicate binaries, so each one
would remain in history permanently; the tag is what ties a release to its
source.

## Licence

GPL v3, as the upstream project. Attribution to ALM Works, Inc. stays intact.
