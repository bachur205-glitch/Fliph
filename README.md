.github/workflows/build.yml# Fliph
name: Build APK
on: [push, workflow_dispatch]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      - run: unzip -q flipdeck.zip
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v5
        with:
          gradle-version: "8.7"
      - run: gradle assembleDebug --no-daemon
        working-directory: flipdeck
      - uses: actions/upload-artifact@v5
        with:
          name: flipdeck-apk
          path: flipdeck/app/build/outputs/apk/debug/app-debug.apk
