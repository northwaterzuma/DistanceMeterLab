#!/usr/bin/env bash
set -euo pipefail
gradle assembleDebug
mkdir -p dist
cp app/build/outputs/apk/debug/*.apk dist/ || true
ls -lh dist || true
