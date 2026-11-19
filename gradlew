#!/usr/bin/env sh
# POSIX compliant Gradle start script for Linux/macOS runners
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
APP_NAME="Gradle"
APP_BASE_NAME=${0##*/}

MAX_FD="maximum"
warn () {
    echo "$*"
} >&2
die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

DEFAULT_JVM_OPTS='"-Xmx2g" "-Dfile.encoding=UTF-8"'
exec gradle -Dorg.gradle.appname="$APP_BASE_NAME" "$@"
