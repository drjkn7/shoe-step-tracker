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
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
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
    implementation(libs.androidx.room3.runtime)
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
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// ---------------------------------------------------------------------------------------------
// Manifest audit (SEC-01, SEC-02, SEC-03, SEC-04). Runs on the final merged manifest of every
// build variant, so permissions or components added by libraries are caught too.
// ---------------------------------------------------------------------------------------------

/** Every permission the app may request. Anything else fails the build. */
val manifestPermissionAllowlist = setOf(
    "android.permission.NFC",
    "android.permission.health.READ_STEPS",
    // Signature-level permission AndroidX Core declares for the app's own receivers.
    "io.github.drjkn7.shoestep.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
)

/** Components allowed to be exported in release, mapped to the permission that must guard them (null = none). */
val manifestReleaseExportAllowlist = mapOf(
    "io.github.drjkn7.shoestep.MainActivity" to null,
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
            allowedPermissions.set(manifestPermissionAllowlist)
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
