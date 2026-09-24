# Dead Packet Society Field Controller 2.0

Newest Android Studio project for the DPS field-controller app.

## What is included

- Dead Packet Society dashboard UI
- Dark futuristic DPS theme based on the supplied reference
- Live OpenStreetMap map using OSMdroid (not Google Maps)
- Current GPS position marker centered/followed on the map
- Wi-Fi signal markers
- BLE signal markers
- DPS-01 through DPS-04 node status
- START / STOP controls using the existing `START_DEF` / `STOP` commands
- AP/observation counter
- Recent observation list
- Separate Communication page with COPY ALL / CLEAR
- Configuration page with WiGLE and WDGWars API fields
- Working BLE controller logic for the current Heltec UUIDs
- 20-byte BLE notification chunk reassembly for the current Heltec transmit behavior
- Persistent bonded-device reconnect / Just Works / no-PIN architecture

## Current DPS architecture

Android app -> bonded BLE -> Heltec WiFi LoRa 32 V3 -> Wi-Fi/TCP -> XIAO ESP32-C5 nodes.

The C5 and Heltec scanner/controller firmware are intentionally not modified by this app package.

## Map

OSMdroid 6.1.20 is used for OpenStreetMap tiles. Google Maps is not included.

Observation markers are placed at the current GPS position when the observation arrives. The field-unit position remains centered on the map.

## Build

Open this folder directly in Android Studio.

Recommended current environment:

- Android Studio Quail 4 or newer
- Android Gradle Plugin 9.4.0
- Kotlin 2.3.21
- JDK 17
- compileSdk 36
- minSdk 26

The first Gradle sync requires internet access to download AndroidX and OSMdroid dependencies.

## Important

This package is the Android app project. It does not replace the already-working Heltec or XIAO firmware.
