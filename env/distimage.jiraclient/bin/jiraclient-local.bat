@echo off

:: ============================================================================
:: Starts Client for Jira with a Java runtime that is known to have JavaFX.
::
:: Why this exists: launch.bat looks for a runtime in three steps,
::   1) %JAVA_HOME%   2) <dist>\jre\   3) javaw.exe from the PATH
:: and step 3 performs no version check. Oracle removed JavaFX from the Java 8
:: updates after 8u202, and com.almworks.launcher.Launcher needs
:: javafx.application.Platform on its very first line. If a newer Java 8 JRE is
:: on the PATH, the application dies with NoClassDefFoundError -- silently,
:: because jiraclient.bat runs javaw.exe and detaches with "start /b".
::
:: Set JIRACLIENT_JAVA_HOME to point at your own JDK, otherwise the path below
:: is used. setlocal keeps JAVA_HOME scoped to this process: no system or user
:: environment variable is modified.
:: ============================================================================

setlocal

:: Default location. Override with JIRACLIENT_JAVA_HOME instead of editing this.
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_202
if not "%JIRACLIENT_JAVA_HOME%"=="" set JAVA_HOME=%JIRACLIENT_JAVA_HOME%

if not exist "%JAVA_HOME%\bin\javaw.exe" goto no_jdk
if not exist "%JAVA_HOME%\jre\lib\ext\jfxrt.jar" goto no_javafx

call "%~dp0jiraclient.bat" %*
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
