package tv.coog.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = confirmIntent(intent) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                AppUpdater.installEvents.tryEmit(InstallEvent.Success)
            }
            else -> {
                Log.w(TAG, "package install status=$status message=$message")
                val text = when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "Install was cancelled."
                    PackageInstaller.STATUS_FAILURE_CONFLICT ->
                        "This APK is signed with a different key. Uninstall Coog, then install the GitHub APK once."
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This APK is not compatible with this device."
                    PackageInstaller.STATUS_FAILURE_INVALID -> "The downloaded APK is invalid."
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage to install the update."
                    else -> message.ifBlank { "Install failed ($status)" }
                }
                AppUpdater.installEvents.tryEmit(InstallEvent.Failed(text))
            }
        }
    }

    private fun confirmIntent(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
    }

    companion object {
        private const val TAG = "CoogUpdate"
    }
}
