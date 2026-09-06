package dikiz.app.crashreport.config

import android.app.Activity
import android.os.Parcelable
import androidx.annotation.DrawableRes
import dikiz.app.crashreport.activity.CrashActivity
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class CrashConfig(
    var backgroundMode: Int = BACKGROUND_MODE_SHOW_CUSTOM,
    var isEnabled: Boolean = true,
    var isShowErrorDetails: Boolean = true,
    var isShowRestartButton: Boolean = true,
    var isLogErrorOnRestart: Boolean = true,
    var isTrackActivities: Boolean = false,
    var minTimeBetweenCrashesMs: Int = 3000,
    @DrawableRes var errorDrawable: Int? = null,
    var errorActivityClass: Class<out Activity>? = null,
    var restartActivityClass: Class<out Activity>? = null,
    var eventListener: @RawValue CrashActivity.EventListener? = null
) : Parcelable {

    companion object {
        const val BACKGROUND_MODE_SILENT = 0
        const val BACKGROUND_MODE_SHOW_CUSTOM = 1
        const val BACKGROUND_MODE_CRASH = 2
    }
}
