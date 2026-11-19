#!/bin/sh
# Minimal Gradle wrapper launcher (runs gradle/wrapper/gradle-wrapper.jar).
app_path=$0
while [ -h "$app_path" ]; do
    ls=$(ls -ld "$app_path")
    link=${ls#*' -> '}
    case $link in
        /*) app_path=$link ;;
        *) app_path=$(dirname "$app_path")/$link ;;
    esac
done
APP_HOME=$(cd "$(dirname "$app_path")" > /dev/null && pwd -P) || exit
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD=$JAVA_HOME/bin/java
else
    JAVACMD=java
fi
exec "$JAVACMD" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew \
    -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
