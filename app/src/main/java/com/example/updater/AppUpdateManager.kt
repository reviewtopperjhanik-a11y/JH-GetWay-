package com.example.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AppReleaseInfo(
    val repoSlug: String,
    val tagName: String,
    val versionName: String,
    val versionCode: Int?,
    val releaseTitle: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val publishedAt: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val expectedSha256: String? = null
)

sealed interface AppUpdateCheckResult {
    data class UpdateAvailable(val release: AppReleaseInfo) : AppUpdateCheckResult
    data class UpToDate(val currentVersionName: String, val latestTag: String) : AppUpdateCheckResult
    data class NoReleasesPublished(val repoSlug: String) : AppUpdateCheckResult
    data class Error(val message: String) : AppUpdateCheckResult
}

sealed interface ApkInstallLaunchResult {
    data object Launched : ApkInstallLaunchResult
    data object UnknownSourcesPermissionRequired : ApkInstallLaunchResult
    data class Failed(val message: String) : ApkInstallLaunchResult
}

object AppUpdateManager {

    private val repoSlugRegex = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

    private val apiClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addNetworkInterceptor { chain ->
                val req = chain.request()
                if (!req.url.isHttps) {
                    throw SecurityException("Only HTTPS update endpoints are permitted.")
                }
                chain.proceed(req)
            }
            .build()
    }

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addNetworkInterceptor { chain ->
                val req = chain.request()
                if (!req.url.isHttps) {
                    throw SecurityException("Only HTTPS APK downloads are permitted.")
                }
                chain.proceed(req)
            }
            .build()
    }

    fun normalizeGithubRepo(rawInput: String): String? {
        var cleaned = rawInput.trim()
        if (cleaned.isEmpty()) return null
        cleaned = cleaned
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        val parts = cleaned.split('/').filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val slug = "${parts[0]}/${parts[1]}"
        return if (repoSlugRegex.matches(slug)) slug else null
    }

    fun isRemoteNewer(
        currentVersionCode: Int,
        currentVersionName: String,
        remoteVersionCode: Int?,
        remoteVersionNameOrTag: String
    ): Boolean {
        if (remoteVersionCode != null && currentVersionCode > 1) {
            return remoteVersionCode > currentVersionCode
        }
        val semVerCmp = compareVersionNames(remoteVersionNameOrTag, currentVersionName)
        if (semVerCmp != 0) return semVerCmp > 0
        if (remoteVersionCode != null) {
            return remoteVersionCode > currentVersionCode
        }
        return false
    }

    fun compareVersionNames(aRaw: String, bRaw: String): Int {
        val aParts = parseVersionParts(aRaw)
        val bParts = parseVersionParts(bRaw)
        val maxLen = maxOf(aParts.size, bParts.size)
        for (i in 0 until maxLen) {
            val av = aParts.getOrElse(i) { 0 }
            val bv = bParts.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    private fun parseVersionParts(raw: String): List<Int> {
        val cleaned = raw.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        return cleaned.split('.').mapNotNull { it.trim().toIntOrNull() }
    }

    suspend fun checkForUpdate(
        repoInput: String,
        currentVersionCode: Int,
        currentVersionName: String
    ): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        val repoSlug = normalizeGithubRepo(repoInput)
            ?: return@withContext AppUpdateCheckResult.Error("Invalid GitHub repository format. Use owner/repo.")

        val url = "https://api.github.com/repos/$repoSlug/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "JH-GetWay-Android/$currentVersionName")
            .get()
            .build()

        runCatching {
            apiClient.newCall(request).execute().use { resp ->
                if (resp.code == 404) {
                    return@withContext AppUpdateCheckResult.NoReleasesPublished(repoSlug)
                }
                if (resp.code == 403 || resp.code == 429) {
                    return@withContext AppUpdateCheckResult.Error("GitHub API rate limit reached. Try again later.")
                }
                if (!resp.isSuccessful) {
                    return@withContext AppUpdateCheckResult.Error("GitHub check failed (HTTP ${resp.code}).")
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext AppUpdateCheckResult.Error("Empty response from GitHub Releases API.")
                }

                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "").trim()
                val releaseTitle = json.optString("name", tagName).ifBlank { tagName }
                val releaseNotes = json.optString("body", "").trim()
                val htmlUrl = json.optString("html_url", "https://github.com/$repoSlug/releases/latest")
                val publishedAt = json.optString("published_at", "")

                val assets = json.optJSONArray("assets")
                var apkUrl = ""
                var apkName = ""
                var apkSize = 0L
                var manifestUrl = ""

                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        val dlUrl = asset.optString("browser_download_url", "")
                        val size = asset.optLong("size", 0L)
                        if (name.equals("update-manifest.json", ignoreCase = true) && dlUrl.startsWith("https://")) {
                            manifestUrl = dlUrl
                        } else if (name.endsWith(".apk", ignoreCase = true) && dlUrl.startsWith("https://")) {
                            val isPreferredReleaseApk = name.contains("release", ignoreCase = true)
                            if (apkUrl.isEmpty() || isPreferredReleaseApk) {
                                apkUrl = dlUrl
                                apkName = name
                                apkSize = size
                            }
                        }
                    }
                }

                if (apkUrl.isBlank()) {
                    return@withContext AppUpdateCheckResult.Error("Latest release ($tagName) has no APK asset attached.")
                }

                var manifestVersionCode: Int? = null
                var manifestVersionName: String? = null
                var expectedSha256: String? = null

                if (manifestUrl.isNotBlank()) {
                    fetchUpdateManifest(manifestUrl, currentVersionName)?.let { m ->
                        manifestVersionCode = m.optInt("versionCode", -1).takeIf { it > 0 }
                        manifestVersionName = m.optString("versionName", "").takeIf { it.isNotBlank() }
                        expectedSha256 = m.optString("sha256", "").takeIf { it.isNotBlank() }
                    }
                }

                val effectiveVersionName = manifestVersionName ?: tagName.removePrefix("v").removePrefix("V")
                val newer = isRemoteNewer(
                    currentVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName,
                    remoteVersionCode = manifestVersionCode,
                    remoteVersionNameOrTag = effectiveVersionName.ifBlank { tagName }
                )

                if (newer) {
                    AppUpdateCheckResult.UpdateAvailable(
                        AppReleaseInfo(
                            repoSlug = repoSlug,
                            tagName = tagName,
                            versionName = effectiveVersionName.ifBlank { tagName },
                            versionCode = manifestVersionCode,
                            releaseTitle = releaseTitle,
                            releaseNotes = releaseNotes,
                            htmlUrl = htmlUrl,
                            publishedAt = publishedAt,
                            apkDownloadUrl = apkUrl,
                            apkFileName = apkName.ifBlank { "JH-GetWay-$tagName.apk" },
                            apkSizeBytes = apkSize,
                            expectedSha256 = expectedSha256
                        )
                    )
                } else {
                    AppUpdateCheckResult.UpToDate(
                        currentVersionName = currentVersionName,
                        latestTag = tagName.ifBlank { effectiveVersionName }
                    )
                }
            }
        }.getOrElse { err ->
            AppUpdateCheckResult.Error(err.message ?: "Unable to reach GitHub Releases.")
        }
    }

    private fun fetchUpdateManifest(manifestUrl: String, currentVersionName: String): JSONObject? {
        return runCatching {
            val req = Request.Builder()
                .url(manifestUrl)
                .header("User-Agent", "JH-GetWay-Android/$currentVersionName")
                .get()
                .build()
            apiClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val raw = resp.body?.string().orEmpty()
                if (raw.isBlank()) null else JSONObject(raw)
            }
        }.getOrNull()
    }

    suspend fun downloadApkWithProgress(
        context: Context,
        release: AppReleaseInfo,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!release.apkDownloadUrl.startsWith("https://")) {
                throw SecurityException("APK download URL must use HTTPS.")
            }

            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            updatesDir.listFiles()?.forEach { old ->
                if (old.isFile && old.name.endsWith(".apk")) old.delete()
            }

            val safeName = release.apkFileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { "jh-getway-update.apk" }
            val tempFile = File(updatesDir, "$safeName.part")
            val finalFile = File(updatesDir, safeName)

            val req = Request.Builder()
                .url(release.apkDownloadUrl)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "JH-GetWay-Android")
                .get()
                .build()

            downloadClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Download failed (HTTP ${resp.code})")
                }
                val body = resp.body ?: throw IllegalStateException("Empty APK response body")
                val totalBytes = body.contentLength().takeIf { it > 0 } ?: release.apkSizeBytes
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                var lastReportedPct = -1

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                if (pct != lastReportedPct) {
                                    lastReportedPct = pct
                                    onProgress(pct)
                                }
                            }
                        }
                        output.flush()
                    }
                }

                val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                val expected = release.expectedSha256?.trim()?.lowercase()
                if (!expected.isNullOrBlank() && actualSha256 != expected) {
                    tempFile.delete()
                    throw SecurityException("APK SHA-256 verification failed. File may be corrupted.")
                }

                if (finalFile.exists()) finalFile.delete()
                if (!tempFile.renameTo(finalFile)) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }
                onProgress(100)
                finalFile
            }
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun createUnknownSourcesSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File): ApkInstallLaunchResult {
        if (!apkFile.exists()) {
            return ApkInstallLaunchResult.Failed("Downloaded APK file not found.")
        }
        if (!canRequestPackageInstalls(context)) {
            return ApkInstallLaunchResult.UnknownSourcesPermissionRequired
        }
        return runCatching {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
            ApkInstallLaunchResult.Launched
        }.getOrElse { err ->
            ApkInstallLaunchResult.Failed(err.message ?: "Unable to launch package installer.")
        }
    }
}
