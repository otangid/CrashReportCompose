package dikiz.app.crashreport.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.annotation.RestrictTo
import androidx.core.content.edit
import dikiz.app.crashreport.config.CrashConfig
import java.io.PrintWriter
import java.io.Serializable
import java.io.StringWriter
import java.lang.ref.WeakReference
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.system.exitProcess


@Suppress("UNCHECKED_CAST")
object CrashActivity {
    interface EventListener : Serializable {
        fun onLaunchErrorActivity()
        fun onRestartAppFromErrorActivity()
        fun onCloseAppFromErrorActivity()
    }

    private const val TAG = "CrashActivity"

    private const val EXTRA_CONFIG = "dikiz.app.crashreport.EXTRA_CONFIG"
    private const val EXTRA_STACK_TRACE = "dikiz.app.crashreport.EXTRA_STACK_TRACE"
    private const val EXTRA_ACTIVITY_LOG = "dikiz.app.crashreport.EXTRA_ACTIVITY_LOG"

    private const val INTENT_ACTION_ERROR_ACTIVITY = "dikiz.app.crashreport.ERROR"
    private const val INTENT_ACTION_RESTART_ACTIVITY = "dikiz.app.crashreport.RESTART"
    private const val CRASH_HANDLER_PACKAGE_NAME = "dikiz.app.crashreport."
    private const val DEFAULT_HANDLER_PACKAGE_NAME = "com.android.internal.os"
    private const val MAX_STACK_TRACE_SIZE = 131071
    private const val MAX_ACTIVITIES_IN_LOG = 50

    private const val SHARED_PREFERENCES_FILE = "dikiz.app.crashreport_preferences"
    private const val SHARED_PREFERENCES_FIELD_TIMESTAMP = "last_crash_timestamp"

    private var application: Application? = null
    var config = CrashConfig()
        private set

    fun setConfig(newConfig: CrashConfig) {
        config = newConfig
    }

    private val activityLog = ArrayDeque<String>(MAX_ACTIVITIES_IN_LOG)
    private var lastActivityCreated = WeakReference<Activity?>(null)
    private var isInBackground = true

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @JvmStatic
    fun install(context: Context?) {
        val appContext = context?.applicationContext as? Application ?: return
        application = appContext

        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (oldHandler?.javaClass?.name?.startsWith(CRASH_HANDLER_PACKAGE_NAME) == true) {
            Log.e(TAG, "CrashActivity was already installed, doing nothing!")
            return
        }

        if (oldHandler != null && !oldHandler.javaClass.name.startsWith(DEFAULT_HANDLER_PACKAGE_NAME)) {
            Log.e(TAG, "IMPORTANT WARNING! You already have an UncaughtExceptionHandler...")
        }

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleCrash(thread, throwable, oldHandler)
        }

        appContext.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var currentlyStartedActivities = 0
            private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity.javaClass != config.errorActivityClass) {
                    lastActivityCreated = WeakReference(activity)
                }
                if (config.isTrackActivities) {
                    logActivity("${dateFormat.format(Date())}: ${activity.javaClass.simpleName} created")
                }
            }

            override fun onActivityStarted(activity: Activity) {
                currentlyStartedActivities++
                isInBackground = currentlyStartedActivities == 0
            }

            override fun onActivityStopped(activity: Activity) {
                currentlyStartedActivities--
                isInBackground = currentlyStartedActivities == 0
            }

            override fun onActivityResumed(activity: Activity) = logActivityEvent(activity, "resumed")
            override fun onActivityPaused(activity: Activity) = logActivityEvent(activity, "paused")
            override fun onActivityDestroyed(activity: Activity) = logActivityEvent(activity, "destroyed")
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            private fun logActivityEvent(activity: Activity, event: String) {
                if (config.isTrackActivities) {
                    logActivity("${dateFormat.format(Date())}: ${activity.javaClass.simpleName} $event")
                }
            }

            private fun logActivity(message: String) {
                if (activityLog.size >= MAX_ACTIVITIES_IN_LOG) activityLog.poll()
                activityLog.add("$message\n")
            }
        })

        Log.i(TAG, "CrashActivity has been installed.")
    }

    private fun handleCrash(thread: Thread, throwable: Throwable, oldHandler: Thread.UncaughtExceptionHandler?) {
        if (!config.isEnabled) {
            oldHandler?.uncaughtException(thread, throwable)
            return
        }

        val context = application?.applicationContext ?: return
        Log.e(TAG, "App has crashed, executing CrashActivity's UncaughtExceptionHandler", throwable)

        if (hasCrashedRecently(context)) {
            Log.e(TAG, "App already crashed recently, not starting custom error activity...")
            oldHandler?.uncaughtException(thread, throwable)
            return
        }

        setLastCrashTimestamp(context, System.currentTimeMillis())
        val errorActivityClass = config.errorActivityClass ?: guessErrorActivityClass(context)

        if (isStackTraceLikelyConflictive(throwable, errorActivityClass)) {
            Log.e(TAG, "Your application class or your error activity have crashed!")
            oldHandler?.uncaughtException(thread, throwable)
            return
        }

        if (config.backgroundMode == CrashConfig.BACKGROUND_MODE_SHOW_CUSTOM || !isInBackground) {
            launchErrorActivity(context, errorActivityClass, throwable)
        } else if (config.backgroundMode == CrashConfig.BACKGROUND_MODE_CRASH) {
            oldHandler?.uncaughtException(thread, throwable)
            return
        }

        lastActivityCreated.get()?.finish()
        killCurrentProcess()
    }

    private fun launchErrorActivity(context: Context, activityClass: Class<out Activity>, throwable: Throwable) {
        val stackTrace = StringWriter().also {
            throwable.printStackTrace(PrintWriter(it))
        }.toString().let {
            if (it.length > MAX_STACK_TRACE_SIZE) {
                val disclaimer = " [stack trace too large]"
                it.substring(0, MAX_STACK_TRACE_SIZE - disclaimer.length) + disclaimer
            } else it
        }

        val intent = Intent(context, activityClass).apply {
            putExtra(EXTRA_STACK_TRACE, stackTrace)
            if (config.isTrackActivities) {
                putExtra(EXTRA_ACTIVITY_LOG, activityLog.joinToString(""))
            }
            if (config.isShowRestartButton && config.restartActivityClass == null) {
                config.restartActivityClass = guessRestartActivityClass(context)
            }
            putExtra(EXTRA_CONFIG, config)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        config.eventListener?.onLaunchErrorActivity()
        context.startActivity(intent)
    }

    private fun hasCrashedRecently(context: Context): Boolean {
        val lastTimestamp = getLastCrashTimestamp(context)
        val currentTimestamp = System.currentTimeMillis()
        return lastTimestamp in 0..currentTimestamp && currentTimestamp - lastTimestamp < config.minTimeBetweenCrashesMs
    }

    private fun getLastCrashTimestamp(context: Context) =
        context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
            .getLong(SHARED_PREFERENCES_FIELD_TIMESTAMP, -1)

    private fun setLastCrashTimestamp(context: Context, timestamp: Long) =
        context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE).edit {
            putLong(SHARED_PREFERENCES_FIELD_TIMESTAMP, timestamp)
        }

    private fun guessErrorActivityClass(context: Context): Class<out Activity> =
        getErrorActivityClassWithIntentFilter(context) ?: DefaultErrorActivity::class.java

    @SuppressLint("QueryPermissionsNeeded")
    private fun getErrorActivityClassWithIntentFilter(context: Context): Class<out Activity>? {
        val searchedIntent = Intent().setAction(INTENT_ACTION_ERROR_ACTIVITY).setPackage(context.packageName)
        val resolveInfos = context.packageManager.queryIntentActivities(searchedIntent, PackageManager.GET_RESOLVED_FILTER)
        return resolveInfos.firstOrNull()?.let {
            try {
                Class.forName(it.activityInfo.name) as Class<out Activity>
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve error activity", e)
                null
            }
        }
    }

    private fun isStackTraceLikelyConflictive(t: Throwable?, activityClass: Class<out Activity>): Boolean {
        var current: Throwable? = t
        while (current != null) {
            if (current.stackTrace.any {
                    (it.className == "android.app.ActivityThread" && it.methodName == "handleBindApplication") ||
                            it.className == activityClass.name
                }) return true
            current = current.cause
        }
        return false
    }

    private fun guessRestartActivityClass(context: Context): Class<out Activity>? =
        getRestartActivityClassWithIntentFilter(context) ?: getLauncherActivity(context)

    @SuppressLint("QueryPermissionsNeeded")
    private fun getRestartActivityClassWithIntentFilter(context: Context): Class<out Activity>? {
        val intent = Intent().setAction(INTENT_ACTION_RESTART_ACTIVITY).setPackage(context.packageName)
        val resolveInfos = context.packageManager.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER)
        return resolveInfos.firstOrNull()?.let {
            try {
                Class.forName(it.activityInfo.name) as Class<out Activity>
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve restart activity", e)
                null
            }
        }
    }

    private fun getLauncherActivity(context: Context): Class<out Activity>? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        return intent?.component?.className?.let {
            try {
                Class.forName(it) as Class<out Activity>
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve launcher activity", e)
                null
            }
        }
    }

    private fun killCurrentProcess() {
        Process.killProcess(Process.myPid())
        exitProcess(10)
    }

    @JvmStatic
    fun getConfigFromIntent(intent: Intent): CrashConfig? {
        val config = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_CONFIG, CrashConfig::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_CONFIG)
        }

        if (config?.isLogErrorOnRestart == true) {
            getStackTraceFromIntent(intent)?.let {
                Log.e(TAG, "The previous app process crashed. Stack trace:\n$it")
            }
        }
        return config
    }

    @JvmStatic
    fun getStackTraceFromIntent(intent: Intent): String? = intent.getStringExtra(EXTRA_STACK_TRACE)

    @JvmStatic
    fun getActivityLogFromIntent(intent: Intent): String? = intent.getStringExtra(EXTRA_ACTIVITY_LOG)

    fun getAllErrorDetailsFromIntent(context: Context, intent: Intent): String {
        val currentDate = Date()
        val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val buildDateAsString: String? = getBuildDateAsString(context, dateFormat)
        val versionName: String? = getVersionName(context)
        var errorDetails = ""
        errorDetails += "Build version: $versionName \n"
        if (buildDateAsString != null) {
            errorDetails += "Build date: $buildDateAsString \n"
        }
        errorDetails += "Current date: " + dateFormat.format(currentDate) + " \n"
        errorDetails += "Device: " + getDeviceModelName() + " \n \n"
        errorDetails += "Stack trace:  \n"
        errorDetails += getStackTraceFromIntent(intent)
        val activityLog = getActivityLogFromIntent(intent)
        if (activityLog != null) {
            errorDetails += "\nUser actions: \n"
            errorDetails += activityLog
        }
        return errorDetails
    }

    private fun getBuildDateAsString(context: Context, dateFormat: DateFormat): String? {
        var buildDate: Long
        try {
            val ai = context.packageManager.getApplicationInfo(context.packageName, 0)
            val zf = ZipFile(ai.sourceDir)
            // If this failed, try with the old zip method
            val ze = zf.getEntry("classes.dex")
            buildDate = ze.getTime()
            zf.close()
        } catch (_: Exception) {
            buildDate = 0
        }
        return if (buildDate > 312764400000L) dateFormat.format(Date(buildDate)) else null
    }

    private fun getVersionName(context: Context): String? {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            return packageInfo.versionName
        } catch (_: Exception) {
            return "Unknown"
        }
    }

    private fun getDeviceModelName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer)) capitalize(model) else capitalize(manufacturer) + " " + model
    }

    private fun capitalize(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        val first = s[0]
        return if (Character.isUpperCase(first)) s else first.uppercaseChar().toString() + s.substring(1)
    }

    fun restartApplication(activity: Activity, config: CrashConfig) {
        val intent = Intent(activity, config.restartActivityClass)
        restartApplicationWithIntent(activity, intent, config)
    }

    private fun restartApplicationWithIntent(activity: Activity, intent: Intent, config: CrashConfig) {
        intent.addFlags((Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        if (intent.component != null) {
            // If the class name has been set, we force it to simulate a Launcher launch.
            // If we don't do this, if you restart from the error activity, then press home,
            // and then launch the activity from the launcher, the main activity appears twice on the
            // backstack.
            // This will most likely not have any detrimental effect because if you set the Intent
            // component,
            // if will always be launched regardless of the actions specified here.
            intent.setAction(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
        }
        if (config.eventListener != null) {
            config.eventListener!!.onRestartAppFromErrorActivity()
        }
        activity.finish()
        activity.startActivity(intent)
        killCurrentProcess()
    }

    fun closeApplication(activity: Activity, config: CrashConfig) {
        if (config.eventListener != null) {
            config.eventListener!!.onCloseAppFromErrorActivity()
        }
        activity.finish()
        killCurrentProcess()
    }
}
