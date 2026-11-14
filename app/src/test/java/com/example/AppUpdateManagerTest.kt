package com.example

import com.example.updater.AppUpdateCheckResult
import com.example.updater.AppUpdateManager
import io.ktor.http.ContentType
import io.ktor.server.cio.CIO
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket

class AppUpdateManagerTest {

    @Test
    fun normalizeGithubRepo_handlesSlugAndFullUrls() {
        assertEquals("jhsoft/jh-getway", AppUpdateManager.normalizeGithubRepo("jhsoft/jh-getway"))
        assertEquals("jhsoft/jh-getway", AppUpdateManager.normalizeGithubRepo("https://github.com/jhsoft/jh-getway"))
        assertEquals("jhsoft/jh-getway", AppUpdateManager.normalizeGithubRepo("https://github.com/jhsoft/jh-getway.git"))
        assertEquals("jhsoft/jh-getway", AppUpdateManager.normalizeGithubRepo("github.com/jhsoft/jh-getway/releases"))
        assertNull(AppUpdateManager.normalizeGithubRepo(""))
        assertNull(AppUpdateManager.normalizeGithubRepo("invalid-slug"))
        assertNull(AppUpdateManager.normalizeGithubRepo("owner/bad repo"))
    }

    @Test
    fun isRemoteNewer_comparesVersionCodeAndSemVerCorrectly() {
        assertTrue(
            AppUpdateManager.isRemoteNewer(
                currentVersionCode = 101,
                currentVersionName = "1.0.1",
                remoteVersionCode = 102,
                remoteVersionNameOrTag = "v1.0.2"
            )
        )
        assertFalse(
            AppUpdateManager.isRemoteNewer(
                currentVersionCode = 102,
                currentVersionName = "1.0.2",
                remoteVersionCode = 102,
                remoteVersionNameOrTag = "v1.0.2"
            )
        )
        assertTrue(
            AppUpdateManager.isRemoteNewer(
                currentVersionCode = 1,
                currentVersionName = "1.0.0",
                remoteVersionCode = null,
                remoteVersionNameOrTag = "v1.0.10"
            )
        )
        assertTrue(AppUpdateManager.compareVersionNames("1.0.10", "1.0.2") > 0)
        assertEquals(0, AppUpdateManager.compareVersionNames("v1.2.3", "1.2.3"))
    }

    @Test
    fun hasMatchingSigningCertificate_checksSha256Intersection() {
        assertTrue(AppUpdateManager.hasMatchingSigningCertificate(setOf("aabb"), setOf("aabb", "ccdd")))
        assertFalse(AppUpdateManager.hasMatchingSigningCertificate(setOf("aabb"), setOf("eeff")))
        assertFalse(AppUpdateManager.hasMatchingSigningCertificate(emptySet(), setOf("aabb")))
    }

    @Test
    fun checkForUpdate_selectsDebugOrReleaseApkAndExpectedSha256() = runBlocking {
        var port = 0
        val server = embeddedServer(CIO, configure = {
            connector {
                host = "127.0.0.1"
                port = 0
            }
            reuseAddress = true
        }) {
            routing {
                get("/repos/jhsoft/jh-getway/releases/latest") {
                    call.respondText(
                        """
                        {
                          "tag_name": "v1.0.5",
                          "name": "JH GetWay v1.0.5",
                          "body": "Fixes package update conflict",
                          "html_url": "https://github.com/jhsoft/jh-getway/releases/tag/v1.0.5",
                          "assets": [
                            {
                              "name": "JH-GetWay-v1.0.5-release.apk",
                              "browser_download_url": "http://127.0.0.1:$port/dl/release.apk",
                              "size": 1000
                            },
                            {
                              "name": "JH-GetWay-v1.0.5-debug.apk",
                              "browser_download_url": "http://127.0.0.1:$port/dl/debug.apk",
                              "size": 1200
                            },
                            {
                              "name": "update-manifest.json",
                              "browser_download_url": "http://127.0.0.1:$port/dl/update-manifest.json",
                              "size": 250
                            }
                          ]
                        }
                        """.trimIndent(),
                        ContentType.Application.Json
                    )
                }
                get("/dl/update-manifest.json") {
                    call.respondText(
                        """
                        {
                          "versionCode": 105,
                          "versionName": "1.0.5",
                          "tag": "v1.0.5",
                          "apkName": "JH-GetWay-v1.0.5-release.apk",
                          "sha256": "relsha256",
                          "debugApkName": "JH-GetWay-v1.0.5-debug.apk",
                          "debugSha256": "dbgsha256",
                          "signingMode": "ci-ephemeral"
                        }
                        """.trimIndent(),
                        ContentType.Application.Json
                    )
                }
            }
        }
        server.start(wait = false)
        try {
            port = server.engine.resolvedConnectors().first().port
            Socket("127.0.0.1", port).use { }
            val base = "http://127.0.0.1:$port"
            val dbgRes = AppUpdateManager.checkForUpdate(
                repoInput = "jhsoft/jh-getway",
                currentVersionCode = 104,
                currentVersionName = "1.0.4",
                preferDebugApk = true,
                apiBaseUrl = base
            ) as AppUpdateCheckResult.UpdateAvailable
            assertEquals("JH-GetWay-v1.0.5-debug.apk", dbgRes.release.apkFileName)
            assertEquals("dbgsha256", dbgRes.release.expectedSha256)
            assertEquals("debug-persistent", dbgRes.release.signingMode)

            val relRes = AppUpdateManager.checkForUpdate(
                repoInput = "jhsoft/jh-getway",
                currentVersionCode = 104,
                currentVersionName = "1.0.4",
                preferDebugApk = false,
                apiBaseUrl = base
            ) as AppUpdateCheckResult.UpdateAvailable
            assertEquals("JH-GetWay-v1.0.5-release.apk", relRes.release.apkFileName)
            assertEquals("relsha256", relRes.release.expectedSha256)
            assertEquals("ci-ephemeral", relRes.release.signingMode)
        } finally {
            runCatching { server.stop(100, 300) }
        }
    }
}
