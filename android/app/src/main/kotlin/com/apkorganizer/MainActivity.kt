package com.apkorganizer

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

/**
 * Main Activity for APK Organizer.
 * Registers the custom ApkManagerPlugin with the Flutter engine.
 */
class MainActivity : FlutterActivity() {

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Register the custom ApkManagerPlugin
        flutterEngine.plugins.add(ApkManagerPlugin())
    }
}
