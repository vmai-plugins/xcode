#!/bin/bash
./gradlew :app:assembleDebug --console=plain
ls -lh app/build/outputs/apk/debug/app-debug.apk
