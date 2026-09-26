package com.example.tramapp.debug

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PromotionSpikeActivity : ComponentActivity() {

    private val tag = "PromotionSpike"
    private var statusText by mutableStateOf("Initializing...")
    private var lastBuiltNotification: Notification? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            postNotification()
        } else {
            statusText = "POST_NOTIFICATIONS denied"
        }
    }

    private var colorized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        colorized = intent.getBooleanExtra("colorized", false)
        if (intent.getBooleanExtra("autopost", false)) {
            checkAndPost()
        }
        
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(statusText)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { colorized = false; checkAndPost() }) {
                            Text("Post spike notification")
                        }
                        Button(onClick = { colorized = true; checkAndPost() }) {
                            Text("Post colorized variant")
                        }
                        Button(onClick = {
                            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            nm.cancel(4242)
                        }) {
                            Text("Cancel")
                        }
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= 36) {
                                val intent = Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS").apply {
                                    putExtra("android.provider.extra.APP_PACKAGE", packageName)
                                }
                                try {
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                    })
                                }
                            } else {
                                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                })
                            }
                        }) {
                            Text("Open promotion settings")
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun checkAndPost() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                postNotification()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            postNotification()
        }
    }

    private fun postNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("promotion_spike", "Promotion Spike", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(this, PromotionSpikeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val styled = SpannableStringBuilder().apply {
            append("A ↗ 120 m → Strossmayerovo nám.  ")
            append("8", StyleSpan(Typeface.BOLD), 0)
            setSpan(ForegroundColorSpan(Color.parseColor("#E53935")), length - 1, length, 0)
            append(" 1m · ")
            append("12", StyleSpan(Typeface.BOLD), 0)
            setSpan(ForegroundColorSpan(Color.parseColor("#1E88E5")), length - 2, length, 0)
            append(" 4m · ")
            append("17", StyleSpan(Typeface.BOLD), 0)
            setSpan(ForegroundColorSpan(Color.parseColor("#43A047")), length - 2, length, 0)
            append(" 9m\n")

            append("B ↘ 150 m → Letenské nám.  ")
            append("12", StyleSpan(Typeface.BOLD), 0)
            setSpan(ForegroundColorSpan(Color.parseColor("#1E88E5")), length - 2, length, 0)
            append(" 2m · ")
            append("8", StyleSpan(Typeface.BOLD), 0)
            setSpan(ForegroundColorSpan(Color.parseColor("#E53935")), length - 1, length, 0)
            append(" 7m\n")

            append("● B → Vltavská  ")
            append("17", StyleSpan(Typeface.BOLD), 0)
            setSpan(ForegroundColorSpan(Color.parseColor("#43A047")), length - 2, length, 0)
            append(" 3m +2")
        }

        val firstLine = styled.subSequence(0, styled.toString().indexOf('\n'))

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, "promotion_spike")
        } else {
            Notification.Builder(this)
        }

        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Kamenická")
            .setContentText(firstLine)
            .setStyle(Notification.BigTextStyle().bigText(styled).setBigContentTitle("Kamenická"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColorized(colorized)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setContentIntent(pendingIntent)

        if (Build.VERSION.SDK_INT >= 36) {
            applyApi36Features(builder)
        }

        val notification = builder.build()
        lastBuiltNotification = notification
        
        nm.notify(4242, notification)

        CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            updateStatus()
        }
    }

    @RequiresApi(36)
    private fun applyApi36Features(builder: Notification.Builder) {
        builder.extras.putBoolean("android.requestPromotedOngoing", true)
        builder.setShortCriticalText("8·1m")
    }

    private fun updateStatus() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var status = "SDK_INT: ${Build.VERSION.SDK_INT}\nDevice: ${Build.MODEL}\n"
        Log.i(tag, "SDK_INT: ${Build.VERSION.SDK_INT}, Device: ${Build.MODEL}")

        if (Build.VERSION.SDK_INT >= 36) {
            try {
                val canPost = nm.canPostPromotedNotifications()
                status += "canPostPromotedNotifications(): $canPost\n"
                Log.i(tag, "canPostPromotedNotifications() = $canPost")
            } catch (e: Exception) {
                status += "canPostPromotedNotifications() check failed\n"
            }
            
            val notif = lastBuiltNotification
            if (notif != null) {
                try {
                    val hasPromo = notif.hasPromotableCharacteristics()
                    status += "hasPromotableCharacteristics(): $hasPromo\n"
                    Log.i(tag, "hasPromotableCharacteristics() = $hasPromo")
                    
                    val isOngoing = (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0
                    Log.i(tag, "Check isOngoing: $isOngoing")
                    Log.i(tag, "Check EXTRA_TITLE: ${notif.extras.getCharSequence(Notification.EXTRA_TITLE) != null}")
                    Log.i(tag, "Check colorized extra: ${notif.extras.getBoolean(Notification.EXTRA_COLORIZED)}")
                    Log.i(tag, "Check custom views: contentView=${notif.contentView}, big=${notif.bigContentView}")
                    Log.i(tag, "Check style template: ${notif.extras.getString(Notification.EXTRA_TEMPLATE)}")
                    Log.i(tag, "Check request: ${notif.extras.getBoolean("android.requestPromotedOngoing", false)}")
                    
                } catch (e: Exception) {
                    status += "hasPromotableCharacteristics() check failed\n"
                }
            }
        }

        if (Build.VERSION.SDK_INT >= 23) {
            val activeNotifs = nm.activeNotifications
            val myNotif = activeNotifs.find { it.id == 4242 }
            if (myNotif != null) {
                val notif = myNotif.notification
                status += "Notification 4242 active.\n"
                
                if (Build.VERSION.SDK_INT >= 36) {
                    var isPromoted = false
                    isPromoted = (notif.flags and Notification.FLAG_PROMOTED_ONGOING) != 0

                    status += "FLAG_PROMOTED_ONGOING: $isPromoted\n"
                    Log.i(tag, "FLAG_PROMOTED_ONGOING: $isPromoted")
                    
                    val reqPromoted = notif.extras?.getBoolean("android.requestPromotedOngoing", false) ?: false
                    status += "Extras requestPromotedOngoing: $reqPromoted\n"
                    Log.i(tag, "Extras requestPromotedOngoing: $reqPromoted")
                }
            } else {
                status += "Notification 4242 NOT active.\n"
            }
        }

        statusText = status
    }
}
