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
| API | `/rest/api/2/search` to `/rest/api/2/search/jql` (CHANGE-2046) | done, verified against a live Cloud instance |
| Sync | "Upload conflict" when setting a field a previous upload had already changed | fixed, as a consequence of the migration landing on v2 |
| API | Unbounded JQL in `LoadCommentVisibility` rejected by the enhanced endpoint | open, not fixed |
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

## Search endpoint migration (CHANGE-2046)

Atlassian retired `/rest/api/*/search` in favour of the enhanced search endpoint.
On Cloud the old path already answers **410 Gone**, so this was not a deprecation
to plan for but a client that could no longer synchronise at all. The replacement
is not a drop-in: `startAt` offset paging becomes a `nextPageToken` cursor, and
there is no total result count any more.

Implemented. What changed:

| File | Change |
|---|---|
| `JqlSearch` | Posts to `api/2/search/jql`, sends `nextPageToken` instead of `startAt`. `addExpand()` removed as dead code; the `TOTAL` and `MAX_RESULTS` response keys removed, since neither is returned any more. |
| `RestQueryPager` | Keeps the cursor and pages until the server stops offering one. `getTotal()` and `setStart()` replaced by `getLoadedCount()` and `isLastPageLoaded()`. |
| `RestIssueProcessor` | Progress message takes one argument instead of two. |
| `message.properties` | `loadQuery.progress.load.next` is now `Loading: {0} issues`, with no percentage. |

Three decisions worth knowing about:

* **End of query is decided by the absence of `nextPageToken`, not by `isLast`.**
  The endpoint documentation notes that `isLast` is not returned by every
  operation, so the cursor is the signal that can be relied on. `isLast` is
  still honoured when it is present.
* **`loadNext()` returns the number of issues actually read**, counted as they
  stream through the SAX handler. Callers must no longer derive a position from
  the return value; the cursor lives in the pager.
* **An explicit `maxResult` still caps the load to a single page.**
  `RestDownloadUpdatedIssues.firstSync()` relies on that to fetch only the most
  recently updated issue.

Losing the total count turned out to be contained, because `getTotal()` had no
callers outside the pager. It only fed loop termination and the progress
readout.

### Why v2 and not v3

The first attempt pointed at `api/3/search/jql`, on the assumption that the newer
version was simply the current one. It is not. **v3 is the ADF-aware counterpart
of v2, not its successor**: it returns rich text fields as Atlassian Document
Format objects, `{"type":"doc","version":1,"content":[...]}`, where v2 returns
plain text. Every other call this client makes is on `api/2`, and its field
parsers expect strings throughout, so searching on v3 fed ADF objects to
`JSONKey.CastConvertor`, which **logs and returns null** rather than throwing:

```
SEVERE Expected class java.lang.String
  {"type":"doc","version":1,"content":[{"type":"paragraph","content":[{"text":"test2","type":"text"}]}]}
	at JSONKey$CastConvertor.convert(JSONKey.java:369)
	at ScalarField.loadValue(ScalarField.java:37)
	at JiraIssueJsonFields.loadIssue(JiraIssueJsonFields.java:65)
	at RestIssueProcessor.invoke(RestIssueProcessor.java:56)
```

Issues therefore downloaded successfully but with description and other rich text
fields silently blanked, and `types.priority` failing to resolve. Atlassian
publishes enhanced search under both versions, so `api/2/search/jql` gives the
cursor paging that CHANGE-2046 requires while leaving the field representation,
and the rest of the client, untouched.

The lesson worth keeping: this migration crossed an **API version** boundary when
only an **endpoint** boundary needed crossing.

### The "Upload conflict" bug this caused

Setting a field on an issue produced *"This issue has conflicting changes on the
server"*, with the merge dialog showing the previously edited field on the Remote
side and the newly edited one only on Local.

The conflict check in `EditIssue.onInitialStateLoaded()` compares, per field, the
local base against the freshly downloaded server value; it does not look at
`updated` or at any version number. The base only advances when
`MyValue.doFinishUpload()` finds the re-downloaded value equal to what was
uploaded, and one divergent field aborts the whole edit, because `loadValues()`
loads every descriptor rather than only the changed ones.

With search running on v3, rich text fields came back as null. The base for those
fields could therefore never match the server, never advanced, and every
subsequent edit of *any* field was rejected as a conflict on the previously
uploaded one. Landing the migration on v2 removes the cause; no change to the
conflict machinery was needed.

### Still open

* **The empty JQL** in `LoadCommentVisibility`, which calls
  `new JqlSearch(JqlQuery.EMPTY).addFields("key").querySingle(session)`. The
  enhanced endpoint requires bounded queries and rejects it:
  *"Le query JQL senza vincoli non sono consentite qui."* Comment visibility
  groups are not loaded. Unaffected by the v2/v3 choice.

`fields: ["*all"]` is **not** a problem on `api/2/search/jql`: the live instance
returns the full field set.

---

## Known risks and roadmap

### v2 is on borrowed time

Landing enhanced search on `api/2/search/jql` is a **tactical** choice, not a
final one. It buys back a working client without touching field representation,
but Atlassian has stated the intent to sunset REST API v2 as a whole. No firm
date has been published.

This is the one external dependency worth watching actively. The canonical source
is the developer changelog, <https://developer.atlassian.com/changelog/>, which
publishes an RSS feed; subscribing to it is cheaper than rediscovering a 410 the
way this fork discovered the `/search` removal.

### Roadmap: full v3 migration

Moving the whole client to v3 is not a find-and-replace over the **44** remaining
`api/2/` call sites. v3 is the ADF-aware counterpart of v2, so the prerequisite
is ADF support:

* **Reading**: an ADF to plain text converter, applied where the field parsers
  currently expect a string. `JSONKey.CastConvertor` is where the mismatch
  surfaces today, silently returning null.
* **Writing**: decide between requesting *rendered fields* and sending ADF for
  the fields the client edits, chiefly `description` and comment bodies. Sending
  plain text to a v3 write endpoint has the mirror-image problem of reading ADF
  with a string parser.

Until that exists, the 44 `api/2/` calls cannot be closed, and any partial move
to v3 risks reintroducing exactly the silent-blanking defect described above.

### Out of scope for now

The other 43 occurrences of the `api/2/` prefix are untouched. The path is
assembled centrally in `RestSession` (`myBaseUrl + "rest/" + path`), but the API
version is hardcoded in every literal, so a general v2 to v3 migration is a
separate piece of work.
