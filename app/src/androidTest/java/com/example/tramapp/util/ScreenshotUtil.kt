package com.example.tramapp.util

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File

/**
 * Captures a device screenshot into a known, agent-pullable directory:
 * `/sdcard/Pictures/trampapp-screenshots/<name>.png` on the device (see
 * docs/verification-harness.md for the pull command).
 *
 * Deliberately NOT under the app's private external-files dir: `connectedAndroidTest`
 * uninstalls the app (and wipes its private storage) after the run completes, before an
 * agent gets a chance to `adb pull`. Public shared storage survives the uninstall.
 */
object ScreenshotUtil {
    private const val DEVICE_DIR = "/sdcard/Pictures/trampapp-screenshots"

    fun capture(rule: ComposeTestRule, name: String): File {
        rule.waitForIdle()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val outDir = File(DEVICE_DIR).apply { mkdirs() }
        val outFile = File(outDir, "$name.png")
        device.takeScreenshot(outFile)
        return outFile
    }
}
