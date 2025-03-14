package com.example.baselineprofile

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This test class generates a basic startup baseline profile for the target package.
 *
 * We recommend you start with this but add important user flows to the profile to improve their performance.
 * Refer to the [baseline profile documentation](https://d.android.com/topic/performance/baselineprofiles)
 * for more information.
 *
 * You can run the generator with the Generate Baseline Profile run configuration,
 * or directly with `generateBaselineProfile` Gradle task:
 * ```
 * ./gradlew :app:generateReleaseBaselineProfile -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
 * ```
 * The run configuration runs the Gradle task and applies filtering to run only the generators.
 *
 * Check [documentation](https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args)
 * for more information about available instrumentation arguments.
 *
 * After you run the generator, you can verify the improvements running the [StartupBenchmarks] benchmark.
 **/
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    private val permissionArray = mutableListOf(
        Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CONTACTS, Manifest.permission.READ_SMS, Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALENDAR, Manifest.permission.GET_ACCOUNTS
    )
    @RequiresApi(Build.VERSION_CODES.Q)
    private val qPermissionArray = permissionArray.plusElement(Manifest.permission.ACCESS_MEDIA_LOCATION)
        .minusElement(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val tPermissionArray = qPermissionArray.plus(arrayOf(
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.POST_NOTIFICATIONS)).minusElement(Manifest.permission.READ_EXTERNAL_STORAGE)

    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) tPermissionArray
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) qPermissionArray
        else permissionArray).toTypedArray()
    )

    @get:Rule(order = 1)
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = "com.example.infotest",
            includeInStartupProfile = true
        ) {
            // This block defines the app's critical user journey. Here we are interested in
            // optimizing for app startup. But you can also navigate and scroll
            // through your most important UI.

            // Start default activity for your app
            pressHome()
            startActivityAndWait()

            device.wait(Until.hasObject(By.res("fully_drawn_marker")), 5_000)

            // TODO Write more interactions to optimize advanced journeys of your app.
            // For example:
            // 1. Wait until the content is asynchronously loaded
            // 2. Scroll the feed content
            // 3. Navigate to detail screen

            // Check UiAutomator documentation for more information how to interact with the app.
            // https://d.android.com/training/testing/other-components/ui-automator
        }
    }
}