package com.sangjun.galaxytuner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * 잠자기 모드(Quick-Doze) — the Android 12+ way.
 *
 * `device_idle_constants` is deprecated and no longer stored on recent One UI builds (writing it
 * leaves the switch off because reading it back returns null). The platform's own battery saver
 * policy reads `battery_saver_constants`, where `quick_doze_enabled=true` switches DeviceIdle to
 * its short STATE_QUICK_DOZE_DELAY path (the same key Galaxy Max Hz writes).
 *
 * So the flow is:
 *  1. 켤 때: the value of `battery_saver_constants` is backed up, `quick_doze_enabled=true` is
 *     merged into it, and the resident service is started.
 *  2. 화면이 꺼지면: `low_power` (the battery saver switch) is turned on — its previous value is
 *     kept — and the system enters quick doze within seconds.
 *  3. 화면이 켜지면: `low_power` goes back to what it was (0 stays 0, an already-on battery saver
 *     stays on).
 *  4. 끌 때: the service stops, `low_power` is restored if it is still ours, and
 *     `battery_saver_constants` goes back to the backed-up value (or is deleted again).
 *
 * Everything here writes Settings.Global with the app's own WRITE_SECURE_SETTINGS, so the service
 * needs no Shizuku and no ADB connection while it runs.
 */
object QuickDoze {

    /** Same preferences file as Tuner, so the switch and the service see the same state. */
    private const val PREFS = "galaxy_tuner"

    private const val KEY_ENABLED = "quick_doze_enabled"
    private const val KEY_BSAVER_BACKUP = "battery_saver_constants_backup"
    private const val KEY_LOW_POWER_BACKUP = "low_power_backup"
    private const val KEY_LOW_POWER_TOUCHED = "low_power_touched"

    /** Settings.Global keys used by this feature. */
    const val BSAVER_KEY = "battery_saver_constants"
    const val LOW_POWER_KEY = "low_power"

    private const val QUICK_DOZE_FLAG = "quick_doze_enabled"

    /** The wrong key v1.0.9 wrote; stripped wherever it is met so it cannot linger. */
    private const val LEGACY_QUICK_DOZE_FLAG = "enable_quick_doze"
    private const val ABSENT = "__absent__"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    /** True when `battery_saver_constants` carries `quick_doze_enabled=true`. */
    fun hasQuickDoze(raw: String?): Boolean = raw
        ?.split(',')
        ?.map { it.trim() }
        ?.any { pair ->
            pair.substringBefore('=').trim() == QUICK_DOZE_FLAG &&
                pair.substringAfter('=', "").trim() == "true"
        } == true

    private fun isQuickDozeKey(pair: String): Boolean = pair.substringBefore('=').trim().let {
        it == QUICK_DOZE_FLAG || it == LEGACY_QUICK_DOZE_FLAG
    }

    /** The same list with the quick-doze flag set (or removed), every other entry kept. */
    internal fun mergeQuickDoze(raw: String?, on: Boolean): String {
        val entries = raw.orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { isQuickDozeKey(it) }
            .toMutableList()
        if (on) entries += "$QUICK_DOZE_FLAG=true"
        return entries.joinToString(",")
    }

    private fun readGlobal(context: Context, key: String): String? = try {
        Settings.Global.getString(context.contentResolver, key)
    } catch (t: Throwable) {
        null
    }

    private fun writeGlobal(context: Context, key: String, value: String?): Boolean = try {
        Settings.Global.putString(context.contentResolver, key, value)
    } catch (t: Throwable) {
        false
    }

    /**
     * Removes the row itself. Needed on Android 17, where `putString(key, null)` no longer deletes
     * `battery_saver_constants`.
     */
    private fun deleteGlobal(context: Context, key: String): Boolean = try {
        context.contentResolver.delete(Settings.Global.CONTENT_URI, "name = ?", arrayOf(key)) > 0
    } catch (t: Throwable) {
        false
    }

    private fun isInteractive(context: Context): Boolean = try {
        context.getSystemService(PowerManager::class.java)?.isInteractive ?: true
    } catch (t: Throwable) {
        true
    }

    /**
     * Turns the feature on: keeps the current `battery_saver_constants`, adds the quick-doze flag,
     * starts the resident service and applies the screen-off step right away when the screen is
     * already off. Returns null on success or a message for the snackbar.
     */
    fun enable(context: Context, canWrite: Boolean): String? {
        if (!canWrite) {
            return "잠자기 모드는 설정 권한(WRITE_SECURE_SETTINGS)을 한 번 받아야 켤 수 있어요."
        }
        val store = prefs(context)
        val current = readGlobal(context, BSAVER_KEY)
        if (!store.contains(KEY_BSAVER_BACKUP)) {
            store.edit().putString(KEY_BSAVER_BACKUP, current ?: ABSENT).apply()
        }
        val merged = mergeQuickDoze(current, on = true)
        if (!writeGlobal(context, BSAVER_KEY, merged) || !hasQuickDoze(readGlobal(context, BSAVER_KEY))) {
            return "잠자기 모드 설정을 쓰지 못했어요."
        }
        store.edit().putBoolean(KEY_ENABLED, true).apply()
        start(context)
        if (!isInteractive(context)) onScreenOff(context)
        return null
    }

    /**
     * Turns the feature off: stops the service, puts `low_power` back if it is still ours and
     * restores the original `battery_saver_constants` (deleting the key when there was none).
     *
     * When there was no backup the row is deleted with a content-resolver delete, because a null
     * write is ignored on Android 17; if the flag survives that, it is cut out of whatever the row
     * holds. The switch is only reported off after the value is read back without the flag.
     */
    fun disable(context: Context, canWrite: Boolean): String? {
        val store = prefs(context)
        stop(context)
        store.edit().putBoolean(KEY_ENABLED, false).apply()
        if (!canWrite) {
            store.edit().remove(KEY_BSAVER_BACKUP).apply()
            return null
        }
        restoreLowPower(context)
        val saved = store.getString(KEY_BSAVER_BACKUP, null)
        // A backup taken before the switch was turned on normally has no flag; it is stripped
        // anyway so no quick-doze flag can come back out of a restore.
        val target = if (saved == null || saved == ABSENT || saved.isBlank()) null
        else mergeQuickDoze(saved, on = false)
        if (!clearQuickDoze(context, target)) {
            return "잠자기 모드를 껐지만 원래 설정을 되돌리지 못했어요. 앱을 다시 열어 한 번 더 꺼 주세요."
        }
        store.edit().remove(KEY_BSAVER_BACKUP).apply()
        return if (hasQuickDoze(readGlobal(context, BSAVER_KEY))) {
            "잠자기 모드를 껐지만 기기 설정에 아직 남아 있어요. 앱을 다시 열어 한 번 더 꺼 주세요."
        } else {
            null
        }
    }

    /**
     * Puts `battery_saver_constants` back to [target] (`null` = the key should not exist) and makes
     * sure no quick-doze flag is left. Returns true when the value read back is clean.
     */
    private fun clearQuickDoze(context: Context, target: String?): Boolean {
        if (target != null) {
            writeGlobal(context, BSAVER_KEY, target)
            if (!hasQuickDoze(readGlobal(context, BSAVER_KEY))) return true
        } else {
            deleteGlobal(context, BSAVER_KEY)
            if (!hasQuickDoze(readGlobal(context, BSAVER_KEY))) return true
        }
        // Either the row is still there after the delete or the restored value carried the flag:
        // cut the flag out of what is stored now, and drop the row if nothing else is left.
        val stripped = mergeQuickDoze(readGlobal(context, BSAVER_KEY), on = false)
        if (stripped.isEmpty()) {
            deleteGlobal(context, BSAVER_KEY)
            if (readGlobal(context, BSAVER_KEY) != null) writeGlobal(context, BSAVER_KEY, "")
        } else {
            writeGlobal(context, BSAVER_KEY, stripped)
        }
        return !hasQuickDoze(readGlobal(context, BSAVER_KEY))
    }

    /** 화면 꺼짐: remember `low_power`, then turn battery saver on so DeviceIdle takes the quick path. */
    fun onScreenOff(context: Context) {
        if (!isEnabled(context)) return
        val store = prefs(context)
        if (store.getBoolean(KEY_LOW_POWER_TOUCHED, false)) return
        val current = readGlobal(context, LOW_POWER_KEY)
        val alreadyOn = current?.trim() == "1"
        store.edit()
            .putString(KEY_LOW_POWER_BACKUP, current ?: ABSENT)
            .putBoolean(KEY_LOW_POWER_TOUCHED, !alreadyOn)
            .apply()
        if (alreadyOn) return
        if (!writeGlobal(context, LOW_POWER_KEY, "1")) {
            store.edit()
                .remove(KEY_LOW_POWER_BACKUP)
                .putBoolean(KEY_LOW_POWER_TOUCHED, false)
                .apply()
        }
    }

    /** 화면 켜짐: put `low_power` back to the value it had before the screen went off. */
    fun onScreenOn(context: Context) {
        if (!isEnabled(context)) return
        restoreLowPower(context)
    }

    private fun restoreLowPower(context: Context) {
        val store = prefs(context)
        val touched = store.getBoolean(KEY_LOW_POWER_TOUCHED, false)
        val saved = store.getString(KEY_LOW_POWER_BACKUP, null)
        if (touched) {
            writeGlobal(context, LOW_POWER_KEY, saved?.takeIf { it != ABSENT } ?: "0")
        }
        store.edit()
            .remove(KEY_LOW_POWER_BACKUP)
            .putBoolean(KEY_LOW_POWER_TOUCHED, false)
            .apply()
    }

    /** Starts the resident service; harmless when it is already running. */
    fun start(context: Context) {
        val intent = Intent(context, QuickDozeService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (t: Throwable) {
            // A background start can be refused; the app tries again next time it opens.
        }
    }

    fun stop(context: Context) {
        try {
            context.stopService(Intent(context, QuickDozeService::class.java))
        } catch (t: Throwable) {
            // nothing to stop
        }
    }

    /** Keeps the service in step with the stored switch, called when the app opens. */
    fun sync(context: Context) {
        if (isEnabled(context)) start(context) else stop(context)
    }
}

/**
 * The small resident service behind the switch. It holds one screen on/off receiver and nothing
 * else: no wake lock, no polling, and a low-importance notification that says what is going on.
 */
class QuickDozeService : Service() {

    private var screenReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> QuickDoze.onScreenOff(this@QuickDozeService)
                    Intent.ACTION_SCREEN_ON -> QuickDoze.onScreenOn(this@QuickDozeService)
                }
            }
        }
        screenReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!QuickDoze.isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        // A service started with startForegroundService() has to report a notification within a few
        // seconds, so a refused foreground start ends the service here instead of being retried in
        // the background.
        if (!showForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        // A restart (or the app's own start) may find battery saver left on while the screen is
        // already back on, so the saved value is put back once here.
        val power = getSystemService(PowerManager::class.java)
        if (power?.isInteractive == true) QuickDoze.onScreenOn(this)
        return START_STICKY
    }

    override fun onDestroy() {
        screenReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        screenReceiver = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "잠자기 모드",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "화면이 꺼질 때 잠깐 절전을 켜는 동안 표시돼요"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tab_battery)
            .setContentTitle("잠자기 모드 (빠른 진입)")
            .setContentText("화면이 꺼지면 절전을 잠깐 켜서 바로 잠자기로 들어가요")
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /** Returns false when the foreground start was refused, so the caller can stop the service. */
    private fun showForeground(): Boolean {
        val notification = notification()
        return try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (t: Throwable) {
            false
        }
    }

    companion object {
        private const val CHANNEL_ID = "quick_doze"
        private const val NOTIFICATION_ID = 4109
    }
}

/** Restarts the resident service after a reboot or an update while the switch is on. */
class QuickDozeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (QuickDoze.isEnabled(context)) QuickDoze.start(context)
            }
        }
    }
}
