@echo off
set JAVA_HOME=C:\Users\tripc\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2
cd /d d:\vmstudio-code
call gradlew.bat :feature:editor:compileDebugKotlin :feature:ai:compileDebugKotlin :app:compileDebugKotlin --console=plain > gradle_build_log.txt 2>&1