# Third-party notices

TempoLock's own source code and official APK permission are governed by
[LICENSE](LICENSE). Third-party components remain under their own licenses.

## Components included in the release APK

The `releaseRuntimeClasspath` resolved on 26 August 2026 contains 115 Maven
coordinates. License metadata from 112 cached POM files declares Apache License
2.0. The three coordinates without a cached POM license declaration are also
published under Apache License 2.0:

- `androidx.dynamicanimation:dynamicanimation:1.0.0`;
- `androidx.startup:startup-runtime:1.1.1`;
- `com.google.guava:listenablefuture:1.0`.

The runtime families include:

- AndroidX, Jetpack Compose and Material 3;
- Kotlin standard library and kotlinx.coroutines;
- Dagger and Hilt;
- JSpecify, JSR-305, Jakarta Inject and javax.inject support annotations/APIs;
- Guava `listenablefuture` compatibility artifact.

These components are licensed under the
[Apache License 2.0](licenses/Apache-2.0.txt). Their respective projects and POM
metadata remain the authoritative source for individual copyright notices.

The release bundle publishes this notice and a complete copy of Apache License
2.0 because the Android package build excludes several duplicate `META-INF`
license resources during packaging.

## Build and test tools not bundled as application runtime code

The source project also references tools and test libraries downloaded by Gradle:

- Android Gradle Plugin, Kotlin plugins, KSP, Hilt compiler, AndroidX Test,
  Espresso and the Compose screenshot plugin: Apache License 2.0;
- JUnit 4.13.2: Eclipse Public License 1.0;
- Robolectric 4.14.1: MIT License;
- JaCoCo 0.8.13: Eclipse Public License 2.0.

These tools are not committed to this repository and are not shipped as runtime
code in the official TempoLock APK. Their upstream distributions provide their
complete license texts and notices.
