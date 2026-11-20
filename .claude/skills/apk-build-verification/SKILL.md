---
name: apk-build-verification
description: Como configurar e verificar o build do MultiGNSS Tracker (Gradle Kotlin DSL, osmdroid, manifest) e confirmar o APK. Use antes de entregar qualquer mudança.
---
# Build e verificação

- AGP 8.13.0, Kotlin 2.1.20 + plugin compose 2.1.20, compileSdk/targetSdk 35, minSdk 24, Java 17.
- JDK: o Java 25 do proot quebra o Kotlin; use `JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk`.
- Manifest: localização fina/aproximada; `INTERNET` e `ACCESS_NETWORK_STATE` só para baixar mapas e mostrar status da rede.
- Sempre: `./gradlew testDebugUnitTest assembleDebug` e conferir `app/build/outputs/apk/debug/app-debug.apk`. Corrigir todo erro antes de entregar.
