package com.example.tramapp.glance

enum class SessionWarning { BATTERY_OPTIMIZED_LONG_SESSION }

object SessionHealth {
    /** Warn when the timeout is >= 2 h or Never (timeoutMin == null) and the app is NOT exempt from battery optimisation. */
    fun check(timeoutMin: Int?, ignoringBatteryOptimizations: Boolean): Set<SessionWarning> {
        val warnings = mutableSetOf<SessionWarning>()
        if (!ignoringBatteryOptimizations) {
            if (timeoutMin == null || timeoutMin >= 120) {
                warnings.add(SessionWarning.BATTERY_OPTIMIZED_LONG_SESSION)
            }
        }
        return warnings
    }

    /** One-line text for the notification status line. */
    fun notificationText(warnings: Set<SessionWarning>): String? {
        if (warnings.contains(SessionWarning.BATTERY_OPTIMIZED_LONG_SESSION)) {
            return "Battery saver may stop this session"
        }
        return null
    }
}
