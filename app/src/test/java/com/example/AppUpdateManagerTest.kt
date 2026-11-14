package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.core.i18n.EnglishStrings
import com.example.core.i18n.LocalStrings
import com.example.core.security.CryptoManager
import com.example.ui.GatewayViewModel
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.JhGetWayTheme
import com.example.updater.AppUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUpdateManagerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: JhGetWayApplication
    private lateinit var viewModel: GatewayViewModel

    @Before
    fun setUp() {
        CryptoManager.resetTestKeyProvider()
        app = ApplicationProvider.getApplicationContext()
        runBlocking(Dispatchers.IO) {
            app.preferences.clearAll()
            app.database.clearAllTables()
        }
        viewModel = GatewayViewModel(app)
    }

    @After
    fun tearDown() {
        runBlocking(Dispatchers.IO) {
            app.gatewayServer.stop()
            app.preferences.clearAll()
            app.database.clearAllTables()
        }
        CryptoManager.resetTestKeyProvider()
    }

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
        assertTrue(
            AppUpdateManager.compareVersionNames("1.0.10", "1.0.2") > 0
        )
        assertEquals(0, AppUpdateManager.compareVersionNames("v1.2.3", "1.2.3"))
    }

    @Test
    fun settingsScreen_savesNormalizedGithubUpdateRepository() {
        composeRule.setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                JhGetWayTheme {
                    SettingsScreen(viewModel = viewModel, onNavigate = {})
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_github_repo_input"))

        composeRule.onNodeWithTag("settings_github_repo_input")
            .assertIsDisplayed()
            .performTextReplacement("https://github.com/jhsoft/jh-getway")

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_github_repo_save_btn"))

        composeRule.onNodeWithTag("settings_github_repo_save_btn")
            .performClick()

        val deadlineNs = System.nanoTime() + 5_000_000_000L
        while (viewModel.effectiveGithubUpdateRepo() != "jhsoft/jh-getway" && System.nanoTime() < deadlineNs) {
            Thread.sleep(50)
            composeRule.waitForIdle()
        }
        assertEquals("jhsoft/jh-getway", viewModel.effectiveGithubUpdateRepo())
    }
}
