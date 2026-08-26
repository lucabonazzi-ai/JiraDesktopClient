# Fork change log

Changes made in this fork on top of Client for Jira 3.9.0 (ALM Works), plus the
build and runtime gotchas found while getting the project to build and run on a
clean machine. See the "About this fork" section of [README.md](../README.md)
for scope and method.

---

## Change log

| Area | Change | Status |
|---|---|---|
| Build | UTF-8 source encoding fix | done |
| Build | Line endings renormalised to LF | done (working copy) |
| Build | `build.sh` untracked, `build.sh.example` added | done |
| Runtime | Oracle JDK 8 **with JavaFX** documented as a hard requirement | done |
| Dev tooling | `bin/jiraclient-local.bat` launcher wrapper | done (build output, not tracked) |
| UI | "Copy Messages" button in the connection wizard | done |
| API | `/rest/api/*/search` to `/rest/api/3/search/jql` (CHANGE-2046) | planned |
| Tests | `ReferredByQueryTests` fails in the full run, passes in isolation | open, not fixed |

---

## Build notes

### Prerequisites

| Requirement | Constraint |
|---|---|
| Oracle JDK 8 | 8u112 to 8u202. Must be a JDK (`javac`), not a JRE. Not OpenJDK, not 9+. |
| Apache Ant | Any 1.9/1.10. The binary distribution is enough. |
| Git Bash or WSL | `ant/build.sh` is a POSIX script; there is no `.bat` equivalent. |

The 8u202 upper bound matters twice over: it is the last Oracle build before the
licence change, **and** the last one that still ships JavaFX. See
[Runtime notes](#runtime-notes).

### UTF-8 source encoding

Without an explicit encoding the build fails during compilation:

```
CoreComponents/src/com/almworks/spellcheck/SpellCheckerConfig.java:43:
  error: unmappable character for encoding Cp1252
```

The `gjc` task defined in `ant/transform.xsl` never sets an `encoding` attribute,
so `javac` falls back to the platform default, which is `Cp1252` on a Windows
machine with a Western European locale. The sources are UTF-8 and some contain
Cyrillic text.

Workaround, applied in `ant/build.sh.example`: pass `-Dfile.encoding=UTF-8` to the
Ant JVM.

> Proper fix, not yet applied: add `encoding="UTF-8"` to the `gjc` task in
> `transform.xsl`, so the build stops depending on the locale.

### Line endings

With `core.autocrlf=true`, the Git for Windows default and set at **system**
level, the checkout rewrites test fixtures to CRLF. Tests that compare generated
output (LF) against a reference file then fail:

```
com.almworks.util.config.MediumToXmlWriterTests.testSpecialCases
  junit.framework.ComparisonFailure: expected <...\r\n...> but was <...\n...>
```

Nine fixtures under `*/tests.rc/` were affected. Fix, at **repository** level
only:

```sh
git config core.autocrlf input
git rm --cached -r .
git reset --hard HEAD
```

> `git reset --hard` also restores `ant/build.sh`. Since this fork keeps that file
> untracked the point is moot, but save any local copy first.

### Windows paths in build.sh

Both variables must be quoted, because the paths contain spaces, and written in
Windows form with forward slashes:

```sh
ANT_HOME="C:/ant/apache-ant-1.10.17"
JDK8_HOME="C:/Program Files/Java/jdk1.8.0_202"
```

A POSIX path such as `/c/Program Files/...` works for the shell, but the same
value is handed to a Windows JVM through `-Djdk`, where it cannot be resolved.

### Building without the test suite

`distAll` lives in `ant/runGenerated.xml` and does not depend on `ALL.test`, so it
can be invoked directly, with no edit to `build.xml`:

```sh
"$JDK8_HOME/bin/java" -Dfile.encoding=UTF-8 -cp "$ANT_HOME/lib/ant-launcher.jar" \
  org.apache.tools.ant.launch.Launcher -f ./runGenerated.xml ALL.compile distAll \
  -Djdk="$JDK8_HOME" -Dbuild.number=9876
```

Every `*.test` target also carries `unless="without.tests"`, so a full build with
`-Dwithout.tests=true` skips them.

A single module can be rebuilt the same way, for example `Engine.compile`.

> `buildDist.jiraclient` starts by deleting `build/.dist/jiraclient`. Close the
> application before running `distAll`, or the running instance is left with a
> partially deleted installation.

### Reference build result

```
Compilation: 0 errors
Tests:       185 classes, 579 tests -> 576 green, 3 red (ReferredByQueryTests only)
Output:      build/.dist/jiraclient/  and  build/.dist/jiraclient-9876.zip (24 MB)
```

---

## Runtime notes

### JavaFX is required

The launcher touches JavaFX on its first line:

```
com.almworks.launcher.Launcher.main(Launcher.java:31)
  -> java.lang.NoClassDefFoundError: javafx/application/Platform
```

Oracle removed JavaFX from Java 8 in the updates after 8u202. To check a candidate
runtime:

```
<JAVA_HOME>/jre/lib/ext/jfxrt.jar     # must exist, around 18 MB
```

### How launch.bat picks a runtime

`bin/launch.bat` searches in three steps, in order:

| # | Source | Condition |
|---|---|---|
| 1 | `%JAVA_HOME%\bin\javaw.exe`, falling back to `%JAVA_HOME%\jre\bin\javaw.exe` | `JAVA_HOME` set and the file exists |
| 2 | `<dist>\jre\bin\javaw.exe` | optional JRE bundled into the distribution |
| 3 | `javaw.exe` from `PATH` | last resort, **no version check** |

Step 3 is the trap. If a recent Java 8 JRE is on the `PATH`, the application is
started with a runtime that has no JavaFX and dies immediately. Worse,
`jiraclient.bat` uses `javaw.exe` (`CONSOLE=no`) and detaches with `start /b`, so
the error is never shown: the process simply fails to appear.

To see the error, run the jar directly:

```sh
cd build/.dist/jiraclient
java -Xmx400m -jar jiraclient.jar
```

`jiraclient_verbose.bat` does not help. It opens a separate console without
redirecting the streams.

Two ways out:

* development: `bin/jiraclient-local.bat`, a wrapper that sets `JAVA_HOME` inside
  `setlocal`, so process scope only, and then calls `jiraclient.bat`. It touches no
  system or user environment variable.
* distribution: bundle a Java 8 runtime with JavaFX under `<dist>/jre/`, so step 2
  applies and the application is self-contained.

### Runtime workspace

The application writes to `%USERPROFILE%\.JIRAClient\`, not into the distribution:

```
config.xml          configuration, including connections
items.db            local SQLite database
log/tracker0.log    application log
system2/store2/     internal store
backup/             automatic config.xml backups
```

The `log/` directory inside the distribution only holds `log.properties`. Runtime
logs do **not** go there.

---

## Known issues

### ReferredByQueryTests

In the full suite it fails with three errors:

```
DBException: error materializing system attributes
  caused by SQLiteException: [14] DB[29] createArray() __IA03
  (cannot allocate virtual table) [unable to open database file]
```

Run on its own it passes 3/3. The generated `ItemStorage.test` target uses
`forkmode="once"`, so every class in the module shares a single JVM.
`ReferredByQueryTests` runs last and arrives with the connection counter at
`DB[29..31]`, by which point SQLite can no longer allocate temporary storage. The
underlying cause is earlier tests not releasing their database connections.

To run it in isolation, reusing the already compiled classes:

```xml
<project name="Isolated" default="one" basedir="REPO/ant">
  <import file="REPO/ant/generated.xml"/>
  <target name="one">
    <junit fork="true" printsummary="true" haltonfailure="false">
      <jvmarg value="-Djava.awt.headless=true"/>
      <formatter type="brief" usefile="false"/>
      <classpath refid="classpath.tests.ItemStorage"/>
      <test name="com.almworks.items.api.ReferredByQueryTests"/>
    </junit>
  </target>
</project>
```

> Possible fixes: `forkmode="perTest"` for the ItemStorage module, which is
> slower, or fixing the teardown of the database fixtures.

---

## Planned work

### Search endpoint migration (CHANGE-2046)

Atlassian is retiring `/rest/api/*/search` in favour of `/rest/api/3/search/jql`.
The new endpoint changes pagination in a way that is not a drop-in replacement:

* `startAt` / `total` offset paging is replaced by a `nextPageToken` cursor
* there is no total result count any more, so progress indicators and any logic
  that pre-computes a page count have to be reworked
