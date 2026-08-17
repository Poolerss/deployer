@echo off
setlocal
set "JAVA_HOME="
if exist "%ProgramFiles%\Java\jdk-25\bin\java.exe" set "JAVA_HOME=%ProgramFiles%\Java\jdk-25"
if exist "%ProgramFiles%\Java\jdk-25.0.2\bin\java.exe" set "JAVA_HOME=%ProgramFiles%\Java\jdk-25.0.2"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Install JDK 25 or set JAVA_HOME to it.
  exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
call "%~dp0mvnw.cmd" %*
