package app.undercover.game

import android.content.Context
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Une version publiée sur GitHub. */
data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long)

/** Mises à jour via les « Releases » du dépôt GitHub public. */
object Updater {
    const val REPO = "Cyberlogic91-dev/undercover"
    const val PAGE = "https://github.com/$REPO/releases/latest"

    fun currentVersion(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

    fun currentCode(ctx: Context): Long = runCatching {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }.getOrDefault(0L)

    /** « 2.10.1 » > « 2.9 » : comparaison numérique, partie par partie. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.trimStart('v', 'V').split('.', '-').map { it.toIntOrNull() ?: 0 }
        val l = local.trimStart('v', 'V').split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Dernière version publiée, ou null si inaccessible. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "Undercover-Android")
            c.connectTimeout = 8000; c.readTimeout = 8000
            if (c.responseCode != 200) return@runCatching null
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val assets = j.getJSONArray("assets")
            var url = ""; var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) { url = a.getString("browser_download_url"); size = a.optLong("size"); break }
            }
            if (url.isEmpty()) null
            else Release(j.getString("tag_name").trimStart('v', 'V'), j.optString("body").trim(), url, size)
        }.getOrNull()
    }

    /** Télécharge l'APK dans le cache ; [progress] reçoit 0..1. */
    suspend fun download(ctx: Context, r: Release, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "update").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "undercover-${r.version}.apk")
        var url = URL(r.apkUrl)
        if (url.protocol != "https") error("Adresse de mise à jour non sécurisée")
        var c: HttpURLConnection
        var hops = 0
        while (true) {
            c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "Undercover-Android")
            c.connectTimeout = 15000; c.readTimeout = 30000
            val code = c.responseCode
            if (code in 300..399 && hops++ < 5) {
                url = URL(url, c.getHeaderField("Location")); c.disconnect()
                if (url.protocol != "https") error("Redirection non sécurisée refusée")
                continue
            }
            if (code != 200) error("Téléchargement impossible ($code)")
            break
        }
        val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
        if (total > MAX_APK_BYTES) error("Fichier de mise à jour trop gros")
        c.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(32 * 1024); var done = 0L
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    o.write(buf, 0, n); done += n
                    if (done > MAX_APK_BYTES) error("Fichier de mise à jour trop gros")
                    if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        if (!sameSigner(ctx, out)) { out.delete(); error("Mise à jour refusée : signature inconnue. Ce fichier ne vient pas du développeur.") }
        out
    }

    private const val MAX_APK_BYTES = 80L * 1024 * 1024

    /**
     * Vérifie AVANT installation que l'APK téléchargé est signé par la même clé que l'appli installée
     * (ou par une clé qui lui succède officiellement). Défense en profondeur : Android le vérifie aussi.
     */
    @Suppress("DEPRECATION")
    fun sameSigner(ctx: Context, apk: File): Boolean = runCatching {
        val pm = ctx.packageManager
        val md = java.security.MessageDigest.getInstance("SHA-256")
        fun digests(sigs: Array<android.content.pm.Signature>?) = sigs.orEmpty().map { md.digest(it.toByteArray()).toList() }.toSet()
        if (Build.VERSION.SDK_INT >= 28) {
            val flag = android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            val archive = pm.getPackageArchiveInfo(apk.path, flag) ?: return false
            if (archive.packageName != ctx.packageName) return false
            val mine = digests(pm.getPackageInfo(ctx.packageName, flag).signingInfo?.apkContentsSigners)
            val info = archive.signingInfo
            val theirs = if (info == null) digests(pm.getPackageArchiveInfo(apk.path, android.content.pm.PackageManager.GET_SIGNATURES)?.signatures)
                         else digests(if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory)
            mine.isNotEmpty() && theirs.intersect(mine).isNotEmpty()
        } else {
            val flag = android.content.pm.PackageManager.GET_SIGNATURES
            val archive = pm.getPackageArchiveInfo(apk.path, flag) ?: return false
            if (archive.packageName != ctx.packageName) return false
            val mine = digests(pm.getPackageInfo(ctx.packageName, flag).signatures)
            mine.isNotEmpty() && digests(archive.signatures) == mine
        }
    }.getOrDefault(false)

    /** Android exige d'autoriser l'appli à installer des mises à jour. */
    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Installation via PackageInstaller. Sur Android 12+, quand l'appli se met à jour elle-même,
     * Android peut installer SANS demander de confirmation (après la toute première mise à jour).
     */
    fun installSession(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("undercover.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java), flags)
            session.commit(pi.intentSender)
        }
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openPage(ctx: Context) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}


/** Reçoit le résultat de l'installation ; si Android demande une confirmation, on l'affiche. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "erreur inconnue"
                Toast.makeText(ctx, "Mise à jour impossible : $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
