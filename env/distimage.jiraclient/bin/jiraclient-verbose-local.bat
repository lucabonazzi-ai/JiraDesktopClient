@echo off

:: ============================================================================
:: Starts Client for Jira with full HTTP diagnostics, on a Java runtime that is
:: known to have JavaFX.
::
:: Same runtime resolution as jiraclient-local.bat -- see the comments there for
:: why it is needed -- but delegates to jiraclient_verbose.bat, which sets
::   -Djiraclient.debug=true -Djira.dump=all -Ddebug.httpclient=true
:: and keeps a console window open.
::
:: jira.dump=all puts HttpDumper at DumpLevel.ALL, which writes every request
:: and response, bodies included, under
::   %USERPROFILE%\.JIRAClient\log\jira\
:: Those dumps contain issue data and request headers. Treat them as sensitive
:: and do not attach them to a public ticket without reading them first.
::
:: Set JIRACLIENT_JAVA_HOME to point at your own JDK, otherwise the path below
:: is used. setlocal keeps JAVA_HOME scoped to this process.
:: ============================================================================

setlocal

:: Default location. Override with JIRACLIENT_JAVA_HOME instead of editing this.
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_202
if not "%JIRACLIENT_JAVA_HOME%"=="" set JAVA_HOME=%JIRACLIENT_JAVA_HOME%

if not exist "%JAVA_HOME%\bin\javaw.exe" goto no_jdk
if not exist "%JAVA_HOME%\jre\lib\ext\jfxrt.jar" goto no_javafx

echo Diagnostics are on. HTTP dumps go to "%USERPROFILE%\.JIRAClient\log\jira".
call "%~dp0jiraclient_verbose.bat" %*
endlocal
exit /b

:no_jdk
echo ==========================================================================
echo ERROR: no Java runtime found at
echo   "%JAVA_HOME%"
echo.
echo Client for Jira needs Oracle JDK 8, update 112 to 202.
echo Set JIRACLIENT_JAVA_HOME to its location, for example:
echo   set JIRACLIENT_JAVA_HOME=C:\Program Files\Java\jdk1.8.0_202
echo ==========================================================================
pause
endlocal
exit /b 1

:no_javafx
echo ==========================================================================
echo ERROR: the Java runtime at
echo   "%JAVA_HOME%"
echo has no JavaFX: jre\lib\ext\jfxrt.jar is missing.
echo.
echo Oracle removed JavaFX from the Java 8 updates after 8u202, and the
echo application launcher requires it. Point JIRACLIENT_JAVA_HOME at an
echo Oracle JDK 8 between 8u112 and 8u202.
echo ==========================================================================
pause
endlocal
exit /b 1
