#!/bin/bash
# Ejecuta las pruebas unitarias con JUnitCore directo (los workers de
# Gradle no corren en este sandbox por el bloqueo de loopback).
set -e
export PATH=$HOME/jdk17/jdk-17.0.21+9/bin:$PATH
cd ~/workspace/semaforos-mx/android
M=$HOME/maven-repo
CP="app/build/tmp/kotlin-classes/debugUnitTest"
CP="$CP:app/build/tmp/kotlin-classes/debug"
CP="$CP:$M/junit/junit/4.13.2/junit-4.13.2.jar"
CP="$CP:$M/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"
CP="$CP:$M/com/squareup/moshi/moshi/1.15.1/moshi-1.15.1.jar"
CP="$CP:$M/com/squareup/moshi/moshi-kotlin/1.15.1/moshi-kotlin-1.15.1.jar"
CP="$CP:$M/com/squareup/okio/okio-jvm/3.6.0/okio-jvm-3.6.0.jar"
CP="$CP:$M/org/jetbrains/kotlin/kotlin-stdlib/2.0.20/kotlin-stdlib-2.0.20.jar"
CP="$CP:$M/org/jetbrains/kotlin/kotlin-reflect/2.0.20/kotlin-reflect-2.0.20.jar"
CP="$CP:$M/org/jetbrains/annotations/13.0/annotations-13.0.jar"
CP="$CP:$M/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.7.3/kotlinx-coroutines-core-jvm-1.7.3.jar"
java -cp "$CP" org.junit.runner.JUnitCore \
  com.alan.semaforosmx.data.aggregation.TimingAggregatorTest \
  com.alan.semaforosmx.data.aggregation.TrustWeightingTest \
  com.alan.semaforosmx.data.repository.ContributionHistoryTest \
  com.alan.semaforosmx.data.repository.MapCorrectionTest \
  com.alan.semaforosmx.sensing.GpsVerificationTest \
  com.alan.semaforosmx.ui.contact.ContactEmailTest \
  com.alan.semaforosmx.update.ReleaseParsingTest \
  com.alan.semaforosmx.update.VersionTest \
  com.alan.semaforosmx.widget.WidgetModeTest \
  com.alan.semaforosmx.widget.TestModeTest
