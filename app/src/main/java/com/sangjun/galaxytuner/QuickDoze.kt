package com.sangjun.galaxytuner

import android.accessibilityservice.AccessibilityService
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/**
 * 잠자기 모드(Quick-Doze) — the Android 12+ way, kept alive by an AccessibilityService.
 *
 * `device_idle_constants` is deprecated and no longer stored on recent One UI builds (writing it
 * leaves the switch off because reading it back returns null). The platform's own battery saver
 * policy reads `battery_saver_constants`, where `quick_doze_enabled=true` switches DeviceIdle to
 * its short STATE_QUICK_DOZE_DELAY path (the same key Galaxy Max Hz writes).
 *
 * So the flow is:
 *  1. 켤 때: the value of `battery_saver_constants` is backed up and `quick_doze_enabled=true` is
 *     merged into it; the accessibility service (below) is allowed once so it stays bound.
 *  2. 화면이 꺼지면: `low_power` (the battery saver switch) is turned on — its previous value is
 *     kept — and the system enters quick doze within seconds.
 *  3. 화면이 켜지면: `low_power` goes back to what it was (0 stays 0, an already-on battery saver
 *     stays on).
 *  4. 끌 때: the accessibility service is taken out of the enabled list again, `low_power` is
 *     restored if it is still ours, and `battery_saver_constants` goes back to the backed-up
 *     value (or is deleted again).
 *
 * The screen on/off listener used to live in a small foreground service. That service had to keep
 * a notification, so One UI kept listing the app under "실행 중인 앱" even with the notification
 * hidden. An AccessibilityService is bound by the system instead: no foreground service, no
 * notification, and nothing in that list — which is how Galaxy Max Hz does it too.
 *
 * Everything here writes Settings.Global with the app's own WRITE_SECURE_SETTINGS, so the feature
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

    /** How long the system gets to bind the accessibility service after it is allowed. */
    private const val BIND_WAIT_STEPS = 12
    private const val BIND_WAIT_MS = 250L

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

    // -----------------------------------------------------------------------------------------
    // Accessibility service (the screen on/off listener)
    // -----------------------------------------------------------------------------------------

    fun serviceComponent(context: Context): ComponentName =
        ComponentName(context.applicationContext, QuickDozeAccessibilityService::class.java)

    private fun isOurComponent(flattened: String, context: Context): Boolean {
        val parsed = ComponentName.unflattenFromString(flattened) ?: return false
        val ours = serviceComponent(context)
        return parsed.packageName == ours.packageName && parsed.className == ours.className
    }

    /** True when our accessibility service is listed in the system's enabled set. */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val raw = try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        } catch (t: Throwable) {
            null
        } ?: return false
        return raw.split(':').any { it.isNotBlank() && isOurComponent(it.trim(), context) }
    }

    /** True once the system has actually bound our service (screen events reach us). */
    fun isAccessibilityConnected(): Boolean = QuickDozeAccessibilityService.connected

    /**
     * Adds or removes only our own entry in `ENABLED_ACCESSIBILITY_SERVICES`; every other
     * accessibility service the user turned on stays exactly as it was. On the last removal the
     * system-wide switch is put back to 0 as well.
     */
    private fun writeAccessibilityEnabled(context: Context, on: Boolean): Boolean = try {
        val resolver = context.contentResolver
        val current = Settings.Secure.getString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val entries = current.split(':').map { it.trim() }.filter { it.isNotEmpty() }
        val others = entries.filterNot { isOurComponent(it, context) }
        val next = if (on) {
            (others + serviceComponent(context).flattenToString()).joinToString(":")
        } else {
            others.joinToString(":")
        }
        // A null write does not delete the row on Android 17, so the empty list is written as "".
        val written = Settings.Secure.putString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            next
        )
        if (!on && others.isEmpty()) {
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        } else {
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        }
        written
    } catch (t: Throwable) {
        false
    }

    /** What [ensureAccessibility] ended up with. */
    enum class Accessibility {
        /** The system has the service bound; screen events reach it. */
        CONNECTED,

        /** Allowed in the settings list but not bound yet (the system usually binds it shortly). */
        ENABLED,

        /** The phone still wants its own one-time allow; the switch cannot work until then. */
        NEEDS_ALLOW
    }

    /**
     * Allows the accessibility service without asking the user when WRITE_SECURE_SETTINGS is there,
     * then waits a few seconds for the system to bind it. [Accessibility.NEEDS_ALLOW] is the case
     * the caller has to explain, because only the phone's own settings screen can finish it.
     */
    suspend fun ensureAccessibility(context: Context, canWrite: Boolean): Accessibility {
        if (isAccessibilityConnected()) return Accessibility.CONNECTED
        if (!canWrite) return Accessibility.NEEDS_ALLOW
        val allowed = if (isAccessibilityEnabled(context)) true
        else writeAccessibilityEnabled(context, on = true)
        repeat(BIND_WAIT_STEPS) {
            if (isAccessibilityConnected()) return Accessibility.CONNECTED
            delay(BIND_WAIT_MS)
        }
        return when {
            !allowed -> Accessibility.NEEDS_ALLOW
            isAccessibilityEnabled(context) -> Accessibility.ENABLED
            else -> Accessibility.NEEDS_ALLOW
        }
    }

    /** Opens the phone's accessibility screen, where our service is allowed with one switch. */
    fun openAccessibilityScreen(): String? {
        val context = AppCtx.context ?: return "앱 정보를 읽지 못했어요."
        return try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            null
        } catch (t: Throwable) {
            "접근성 설정을 열지 못했어요. 설정 > 접근성 > 설치된 앱에서 Galaxy Tuner를 켜 주세요."
        }
    }

    // -----------------------------------------------------------------------------------------
    // The switch itself
    // -----------------------------------------------------------------------------------------

    /**
     * Turns the feature on: keeps the current `battery_saver_constants`, adds the quick-doze flag,
     * clears the v1.0.11 foreground service out of the way and applies the screen-off step right
     * away when the screen is already off. The accessibility service is allowed by the caller
     * ([ensureAccessibility]) because that part has to wait for the system.
     * Returns null on success or a message for the snackbar.
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
        // Only the write itself is checked. Recent One UI builds empty `battery_saver_constants`
        // again moments later; reading the flag back here used to fail after a successful write
        // and left the switch off. The service re-applies the flag on every screen-off.
        if (!writeGlobal(context, BSAVER_KEY, merged)) {
            return "잠자기 모드 설정을 쓰지 못했어요."
        }
        store.edit().putBoolean(KEY_ENABLED, true).apply()
        stopLegacyService(context)
        if (!isInteractive(context)) onScreenOff(context)
        return null
    }

    /**
     * Turns the feature off: takes our accessibility service back out of the enabled list, puts
     * `low_power` back if it is still ours and restores the original `battery_saver_constants`
     * (deleting the key when there was none).
     *
     * When there was no backup the row is deleted with a content-resolver delete, because a null
     * write is ignored on Android 17; if the flag survives that, it is cut out of whatever the row
     * holds. The switch is only reported off after the value is read back without the flag.
     */
    fun disable(context: Context, canWrite: Boolean): String? {
        val store = prefs(context)
        stopLegacyService(context)
        store.edit().putBoolean(KEY_ENABLED, false).apply()
        if (!canWrite) {
            store.edit().remove(KEY_BSAVER_BACKUP).apply()
            return null
        }
        // Nothing is left for the accessibility service to do once the switch is off.
        if (isAccessibilityEnabled(context)) writeAccessibilityEnabled(context, on = false)
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
        // The platform clears the flag on some One UI builds, so it is put back before battery
        // saver is turned on — otherwise `low_power=1` would take the normal, slow doze path.
        ensureQuickDozeFlag(context)
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

    /** Puts `quick_doze_enabled=true` back into `battery_saver_constants` if it went missing. */
    private fun ensureQuickDozeFlag(context: Context) {
        val current = readGlobal(context, BSAVER_KEY)
        if (hasQuickDoze(current)) return
        writeGlobal(context, BSAVER_KEY, mergeQuickDoze(current, on = true))
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

    /**
     * v1.0.11 kept a foreground service running for the screen listener. It is gone in v1.0.12, but
     * an install that updates over v1.0.11 can still have the old one up until the package is
     * replaced — so it is stopped wherever the app gets a chance.
     */
    fun stopLegacyService(context: Context) {
        try {
            context.stopService(Intent(context, QuickDozeService::class.java))
        } catch (t: Throwable) {
            // nothing to stop
        }
    }

    /**
     * Best-effort: puts our entry back into the enabled list without waiting and without opening a
     * screen. Used on boot/update and when the app opens with the switch on.
     */
    internal fun restoreAccessibilityEntry(context: Context) {
        if (isAccessibilityEnabled(context)) return
        writeAccessibilityEnabled(context, on = true)
    }

    /** Keeps the feature in step with the stored switch, called when the app opens. */
    fun sync(context: Context) {
        stopLegacyService(context)
        if (!isEnabled(context)) return
        // The system normally keeps the binding across restarts; when the entry was lost (an update
        // can drop it) it is put back here without opening any screen.
        if (!isAccessibilityEnabled(context) && Access.hasSecureWrite()) {
            restoreAccessibilityEntry(context)
        }
    }
}

/**
 * The screen on/off listener, bound by the system as an accessibility service.
 *
 * It reads nothing and controls nothing: the config XML turns window content off, and
 * [onAccessibilityEvent] is empty. Only the screen on/off broadcasts are used, and the whole
 * screen-off step is a couple of Settings writes in [QuickDoze].
 */
class QuickDozeAccessibilityService : AccessibilityService() {

    private var screenReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        registerScreenReceiver()
        // A fresh binding (reboot, update, re-allow) may meet battery saver still on from an earlier
        // cycle, so the saved value is put back once when the screen is already on.
        val power = getSystemService(PowerManager::class.java)
        if (power?.isInteractive == true) QuickDoze.onScreenOn(this)
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> QuickDoze.onScreenOff(this@QuickDozeAccessibilityService)
                    Intent.ACTION_SCREEN_ON -> QuickDoze.onScreenOn(this@QuickDozeAccessibilityService)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        try {
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            screenReceiver = receiver
        } catch (t: Throwable) {
            // The binding can go away while registering; the next connection registers again.
        }
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        screenReceiver = null
    }

    /** Nothing to read: the service exists only for the screen on/off broadcasts. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        connected = false
        unregisterScreenReceiver()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connected = false
        unregisterScreenReceiver()
        super.onDestroy()
    }

    companion object {
        /** True while the system has the service bound. Read by the switch screen. */
        @Volatile
        var connected: Boolean = false
            private set
    }
}

/**
 * The v1.0.11 foreground service, kept as an empty component only so an upgrade can stop it and so
 * no old intent can start something that would show up again. It never starts a foreground
 * notification: anything that reaches it ends immediately.
 */
class QuickDozeService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/** Restarts the binding after a reboot or an update while the switch is on. */
class QuickDozeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                QuickDoze.stopLegacyService(context)
                if (!QuickDoze.isEnabled(context)) return
                // The accessibility entry usually survives a reboot, but an app update can drop it;
                // putting it back here saves the user from allowing it again (other services keep
                // their place in the list).
                if (!QuickDoze.isAccessibilityEnabled(context)) {
                    QuickDoze.restoreAccessibilityEntry(context)
                }
                val power = context.getSystemService(PowerManager::class.java)
                if (power?.isInteractive == true) QuickDoze.onScreenOn(context)
            }
        }
    }
}
