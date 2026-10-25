import com.android.build.api.artifact.SingleArtifact
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room3)
}

room3 {
    // Exported schema JSON is committed so migrations can be tested (Room adds it to androidTest assets).
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "io.github.drjkn7.shoestep"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.drjkn7.shoestep"
        minSdk = 28
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Debug builds are a separate app (".debug"), so tests can run on a phone that has the real,
        // release-signed app without replacing it: a debug build can't install over it (different key),
        // and uninstalling the real app would delete its data. Labelled "Shoe Step (test)" (src/debug).
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // Phase 8 (product owner): shrink, optimise and obfuscate the app's own code too, not just
            // the libraries. Keep rules: src/main/keepRules/rules.keep.
            optimization {
                enable = true
            }
        }
        // The release build exactly (R8 on), signed with the debug key so the full on-device test
        // suite can run against it: ./gradlew connectedStagingAndroidTest -PtestBuildType=staging
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    // Staging uses release's code (the no-op DebugTools in src/release), so it is the release app.
    sourceSets {
        getByName("staging") {
            kotlin.directories += "src/release/java"
        }
    }
    // On-device tests run against debug unless -PtestBuildType=staging is given (see "staging" above).
    testBuildType = providers.gradleProperty("testBuildType").getOrElse("debug")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        managedDevices {
            localDevices {
                // Emulator for on-device tests in CI: ./gradlew pixel2api30DebugAndroidTest
                // https://developer.android.com/studio/test/managed-devices
                create("pixel2api30") {
                    device = "Pixel 2"
                    apiLevel = 30
                    // Automated Test Device image: headless and faster; no Google services needed.
                    systemImageSource = "aosp-atd"
                    // Explicit so AGP 10 (default arm64-v8a) does not break this x86-only image.
                    testedAbi = "x86"
                }
            }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core) // version pin only; see libs.versions.toml
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.health.connect.client)
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.androidx.room3.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room3.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // Same test-only host screen for the staging (R8) test build; never part of release.
    "stagingImplementation"(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// ---------------------------------------------------------------------------------------------
// Manifest audit (SEC-01, SEC-02, SEC-03, SEC-04). Runs on the final merged manifest of every
// build variant, so permissions or components added by libraries are caught too.
// ---------------------------------------------------------------------------------------------

/** Every permission the app (application id [appId]) may request. Anything else fails the build. */
fun manifestPermissionAllowlist(appId: String) = setOf(
    "android.permission.NFC",
    "android.permission.health.READ_STEPS",
    // M2 Phase 4, approved by the product owner on 2026-10-22.
    "android.permission.health.READ_DISTANCE",
    // M2 Phase 7, the session notification (Android 13+). Approved by the product owner on 2026-10-22
    // and again with the Phase 7 plan on 2026-10-25.
    "android.permission.POST_NOTIFICATIONS",
    // Signature-level permission AndroidX Core declares for the app's own receivers (named after the
    // application id, so the debug build's carries ".debug").
    "$appId.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
)

/** Components allowed to be exported in release, mapped to the permission that must guard them (null = none). */
val manifestReleaseExportAllowlist = mapOf(
    "io.github.drjkn7.shoestep.MainActivity" to null,
    // NFC tag entry points (Phase 6). Android 17+: only the NFC service may open it.
    "io.github.drjkn7.shoestep.NfcTagEntry" to "android.permission.DISPATCH_NFC_MESSAGE",
    // Android 9-16 only (disabled on 17+): those versions don't define DISPATCH_NFC_MESSAGE.
    "io.github.drjkn7.shoestep.NfcTagEntryLegacy" to null,
    // Health Connect privacy rationale (Phase 5, product owner approved). Shows static text only.
    // Android 13 and lower: the Health Connect app opens it via ACTION_SHOW_PERMISSIONS_RATIONALE.
    "io.github.drjkn7.shoestep.steps.PermissionsRationaleActivity" to null,
    // Android 14+: only the system (holder of START_VIEW_PERMISSION_USAGE) may open the alias.
    "io.github.drjkn7.shoestep.ViewPermissionUsageActivity" to "android.permission.START_VIEW_PERMISSION_USAGE",
    // Added by connect-client (Android 9-13 Health Connect app binds to it to check the app is in the
    // foreground). No manifest guard exists; its code rejects every caller except the Health Connect
    // package (verified in 1.1.0 bytecode). Product owner approved 2026-10-17. Re-check on upgrades.
    "androidx.health.platform.client.impl.sdkservice.HealthDataSdkService" to null,
    // AndroidX ProfileInstaller: only callable by the system/shell holding DUMP.
    "androidx.profileinstaller.ProfileInstallReceiver" to "android.permission.DUMP",
)

abstract class VerifyManifestTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input abstract val allowedPermissions: SetProperty<String>
    @get:Input abstract val allowedExports: MapProperty<String, String>
    @get:Input abstract val checkExports: Property<Boolean>

    @get:OutputFile abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val ns = "http://schemas.android.com/apk/res/android"
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val doc = factory.newDocumentBuilder().parse(mergedManifest.get().asFile)
        val packageName = doc.documentElement.getAttribute("package")
        val problems = mutableListOf<String>()

        fun elements(tag: String): List<Element> {
            val nodes = doc.getElementsByTagName(tag)
            return (0 until nodes.length).map { nodes.item(it) as Element }
        }

        // SEC-01 / SEC-04: permissions
        val requested = (elements("uses-permission") + elements("uses-permission-sdk-23"))
            .map { it.getAttributeNS(ns, "name") }
        if ("android.permission.INTERNET" in requested) {
            problems += "INTERNET permission found. This app must never have network access (SEC-01)."
        }
        requested.filter { it !in allowedPermissions.get() && it != "android.permission.INTERNET" }
            .forEach { problems += "Permission not on the allowlist: $it (SEC-04)." }

        // SEC-02: backups off
        val app = elements("application").single()
        if (app.getAttributeNS(ns, "allowBackup") != "false") {
            problems += "android:allowBackup must be \"false\" (SEC-02)."
        }

        // SEC-03: only allowlisted components are reachable from other apps
        if (checkExports.get()) {
            val componentTags = listOf("activity", "activity-alias", "service", "receiver", "provider")
            for (component in componentTags.flatMap { elements(it) }) {
                val rawName = component.getAttributeNS(ns, "name")
                val name = if (rawName.startsWith(".")) packageName + rawName else rawName
                val exportedAttr = component.getAttributeNS(ns, "exported")
                val hasIntentFilter = component.getElementsByTagName("intent-filter").length > 0
                val exported = exportedAttr == "true" || (exportedAttr.isEmpty() && hasIntentFilter)
                if (!exported) continue
                val allowed = allowedExports.get()
                if (name !in allowed) {
                    problems += "Exported component not on the allowlist: $name (SEC-03)."
                    continue
                }
                val requiredPermission = allowed.getValue(name)
                if (requiredPermission.isNotEmpty() && component.getAttributeNS(ns, "permission") != requiredPermission) {
                    problems += "$name must be protected by $requiredPermission (SEC-03)."
                }
            }
        }

        val reportFile = report.get().asFile
        if (problems.isNotEmpty()) {
            reportFile.writeText("FAILED\n" + problems.joinToString("\n"))
            throw GradleException("Manifest audit failed:\n - " + problems.joinToString("\n - "))
        }
        reportFile.writeText("OK\npermissions=$requested\n")
    }
}

androidComponents {
    onVariants { variant ->
        val suffix = variant.name.replaceFirstChar { it.uppercase() }
        tasks.register<VerifyManifestTask>("verify${suffix}Manifest") {
            group = "verification"
            description = "Audits the merged ${variant.name} manifest against SEC-01..04."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            allowedPermissions.set(variant.applicationId.map { manifestPermissionAllowlist(it) })
            // Map values cannot be null in Gradle inputs; "" means "no guard permission required".
            allowedExports.set(manifestReleaseExportAllowlist.mapValues { it.value.orEmpty() })
            // Debug builds include tooling activities (Compose preview/test); exports are audited on release.
            checkExports.set(variant.buildType == "release")
            report.set(layout.buildDirectory.file("reports/manifest-audit/${variant.name}.txt"))
        }
    }
}

val verifyNoInternetPermission = tasks.register("verifyNoInternetPermission") {
    group = "verification"
    description = "Fails if any variant's merged manifest has INTERNET, unapproved permissions, backups or exports."
    dependsOn(tasks.withType<VerifyManifestTask>())
}

tasks.named("check") { dependsOn(verifyNoInternetPermission) }
