package com.varuna.openfuel.update

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.varuna.openfuel.AppContainer
import com.varuna.openfuel.BuildConfig
import com.varuna.openfuel.core.net.HttpClient
import com.varuna.openfuel.core.update.Releases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * In-app updates, ported from Rustify: GitHub releases for the check, the APK
 * matching the device's ABI downloaded into the cache, then the system installer.
 */
class AppUpdate(http: HttpClient) {

    private val releases = Releases(http)

    /** Null when up to date; throws when GitHub cannot be reached. */
    suspend fun check(): Releases.Update? = withContext(Dispatchers.IO) {
        releases.check(BuildConfig.VERSION_NAME, Build.SUPPORTED_ABIS.toList())
    }

    /** Downloads the APK into `cacheDir/updates/`, reporting 0..1, or -1 when the size is unknown. */
    suspend fun download(context: Context, apk: Releases.Asset, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, UPDATES_DIR).apply { mkdirs() }
            // Never keep an old APK around: they are 15 MB each.
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, apk.name)
            val connection = URI(apk.url).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", AppContainer.USER_AGENT)
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: apk.size.takeIf { it > 0 } ?: -1L
                connection.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            onProgress(if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f)
                        }
                    }
                }
            } catch (e: Exception) {
                out.delete()
                throw e
            } finally {
                connection.disconnect()
            }
            out
        }

    /** An app that opens APKs: the system installer, Install With Options (Shizuku), SAI… */
    data class Installer(val label: String, val packageName: String, val activityName: String) {
        /**
         * Only the system installer checks openfuel's own "install unknown apps" permission.
         * A Shizuku-based one installs with its own rights, which is the only way left
         * under Android's Advanced Protection, where that permission cannot be granted.
         */
        val isSystem: Boolean get() = packageName.endsWith(".packageinstaller")
    }

    companion object {
        private const val UPDATES_DIR = "updates"

        fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

        /** The per-app "install unknown apps" screen. */
        fun requestInstallPermission(context: Context) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
            runCatching { context.startActivity(intent) }
        }

        private fun apkUri(context: Context, apk: File) =
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)

        private fun viewIntent(context: Context, apk: File) = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri(context, apk), MIME_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        /** Every app that can install [apk]; needs the package-archive `<queries>` entry on Android 11+. */
        fun installers(context: Context, apk: File): List<Installer> {
            val pm = context.packageManager
            return runCatching {
                pm.queryIntentActivities(viewIntent(context, apk), 0).mapNotNull { ri ->
                    val ai = ri.activityInfo ?: return@mapNotNull null
                    Installer(ri.loadLabel(pm).toString(), ai.packageName, ai.name)
                }
                    .filter { it.packageName != context.packageName }
                    .distinctBy { it.packageName }
                    // The system installer first, the rest by name.
                    .sortedWith(compareBy<Installer> { !it.isSystem }.thenBy { it.label.lowercase() })
            }.getOrDefault(emptyList())
        }

        /** Opens [apk] with [installer], sending the user to grant the permission first if the system one needs it. */
        fun installWith(context: Context, apk: File, installer: Installer) {
            if (installer.isSystem && !canInstall(context)) {
                requestInstallPermission(context)
                return
            }
            // An installer uninstalled or disabled since it was listed: the chooser still works.
            runCatching {
                context.grantUriPermission(installer.packageName, apkUri(context, apk), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(viewIntent(context, apk).setClassName(installer.packageName, installer.activityName))
            }.onFailure { installWithChooser(context, apk) }
        }

        /** The system chooser, for whatever [installers] could not see. */
        fun installWithChooser(context: Context, apk: File) {
            context.startActivity(Intent.createChooser(viewIntent(context, apk), null))
        }

        private const val MIME_APK = "application/vnd.android.package-archive"
    }
}
