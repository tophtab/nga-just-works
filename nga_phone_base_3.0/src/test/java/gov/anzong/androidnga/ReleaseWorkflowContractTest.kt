package gov.anzong.androidnga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseWorkflowContractTest {
    private val repositoryRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, ".github/workflows/build.yml").isFile }

    @Test
    fun previewBuildKeepsProductionIdentityAndIsDebuggableWithoutMinification() {
        val gradle = File(repositoryRoot, "nga_phone_base_3.0/build.gradle").readText()
        val release = buildTypeBlock(gradle, "release")
        val debug = buildTypeBlock(gradle, "debug")
        val preview = buildTypeBlock(gradle, "preview")

        assertTrue(gradle.contains("applicationId \"com.github.tophtab.ngajustworks\""))
        assertTrue(debug.contains("applicationIdSuffix '.debug'"))
        assertTrue(preview.contains("initWith release"))
        assertTrue(preview.contains("matchingFallbacks = ['release']"))
        assertTrue(preview.contains("debuggable true"))
        assertTrue(preview.contains("minifyEnabled false"))
        assertTrue(preview.contains("signingConfig signingConfigs.release"))
        assertFalse(preview.contains("applicationIdSuffix"))

        assertTrue(release.contains("debuggable false"))
        assertTrue(release.contains("jniDebuggable false"))
        assertTrue(release.contains("renderscriptDebuggable false"))
        assertTrue(release.contains("minifyEnabled true"))
        assertTrue(release.contains("signingConfig signingConfigs.release"))
    }

    @Test
    fun checkoutKeepsBranchHistoryAndUsesShallowTagsWithBloblessPartialClone() {
        val workflow = File(repositoryRoot, ".github/workflows/build.yml").readText()
        val checkout = stepBody(workflow, "Checkout project sources", "Derive release identity")
        val depthExpression = Regex(
            "fetch-depth:\\s*\\$\\{\\{\\s*startsWith\\(github\\.ref, 'refs/tags/'\\)\\s*&&\\s*(\\d+)\\s*\\|\\|\\s*(\\d+)\\s*\\}\\}",
        ).find(checkout)

        assertEquals(1, Regex("(?m)^\\s*uses: actions/checkout@v4\\s*$").findAll(checkout).count())
        requireNotNull(depthExpression) { "Checkout depth must branch on stable tag refs" }
        assertEquals("Stable tag checkout must fetch only the tagged commit", 1, depthExpression.groupValues[1].toInt())
        assertEquals("Every branch preview checkout must retain complete history", 0, depthExpression.groupValues[2].toInt())
        assertEquals(1, Regex("(?m)^\\s*filter: blob:none\\s*$").findAll(checkout).count())

        val identity = stepBody(workflow, "Derive release identity", "Setup Java")
        assertTrue(identity.contains("git tag --merged \"\$GITHUB_SHA\" --sort=-version:refname"))
    }

    @Test
    fun sharedSdkAndPublishedApkChecksStayPinnedToApi29And36() {
        val rootGradle = File(repositoryRoot, "build.gradle").readText()
        val appGradle = File(repositoryRoot, "nga_phone_base_3.0/build.gradle").readText()
        val workflow = File(repositoryRoot, ".github/workflows/build.yml").readText()
        val staging = stepBody(workflow, "Verify and stage APK", "Create GitHub Release")

        assertTrue(rootGradle.contains("minSdkVersion = 29"))
        assertTrue(rootGradle.contains("targetSdkVersion = 36"))
        assertTrue(rootGradle.contains("compileSdkVersion = 36"))
        assertTrue(appGradle.contains("minSdkVersion project.minSdkVersion"))
        assertTrue(appGradle.contains("targetSdkVersion project.targetSdkVersion"))
        assertTrue(appGradle.contains("compileSdk project.compileSdkVersion"))
        assertTrue(appGradle.contains("abiFilters 'arm64-v8a'"))
        val androidModuleGradles = requireNotNull(repositoryRoot.listFiles())
            .map { File(it, "build.gradle") }
            .filter { it.isFile && it.readText().contains("com.android.") }
        assertTrue(androidModuleGradles.isNotEmpty())
        androidModuleGradles.forEach { moduleGradle ->
            assertTrue(
                "${moduleGradle.parentFile?.name ?: moduleGradle.path} must inherit the shared minSdk",
                Regex("minSdk(?:Version)?\\s+project\\.minSdkVersion")
                    .containsMatchIn(moduleGradle.readText()),
            )
        }
        assertTrue(staging.contains("test \"\$(apkanalyzer manifest min-sdk \"\$release_apk\")\" = \"29\""))
        assertTrue(staging.contains("test \"\$(apkanalyzer manifest target-sdk \"\$release_apk\")\" = \"36\""))
    }

    @Test
    fun eachJobRunsExactlyOneGradleInvocationCarryingItsReleaseTasks() {
        val workflow = File(repositoryRoot, ".github/workflows/build.yml").readText()

        assertEquals(1, "\\./gradlew".toRegex().findAll(workflow).count())
        assertFalse(workflow.contains("printAppVersion"))
        assertEquals(1, "verifyReleaseTag".toRegex().findAll(workflow).count())

        assertTrue(workflow.contains("gradle_tasks=\"verifyReleaseTag :nga_phone_base_3.0:assembleRelease\""))
        assertTrue(workflow.contains("gradle_tasks=\":nga_phone_base_3.0:assemblePreview\""))
        assertTrue(workflow.contains("echo \"gradle_tasks=\$gradle_tasks\""))

        val build = stepBody(workflow, "Build signed APK", "Verify and stage APK")
        assertTrue(build.contains("GRADLE_TASKS: \${{ steps.release.outputs.gradle_tasks }}"))
        assertTrue(build.contains("RELEASE_TAG: \${{ steps.release.outputs.tag }}"))
        assertTrue(build.contains("read -r -a gradle_tasks <<< \"\$GRADLE_TASKS\""))
        assertTrue(build.contains("./gradlew \"\${gradle_tasks[@]}\" --no-daemon"))

        val staging = stepBody(workflow, "Verify and stage APK", "Create GitHub Release")
        assertTrue(staging.contains("app_version=\"\$CI_VERSION_NAME\""))
        assertTrue(staging.contains("manifest version-name"))
        assertTrue(staging.contains("manifest version-code"))
        assertFalse("Staging must not start Gradle", staging.contains("gradlew"))

        val publication = stepBody(workflow, "Create GitHub Release", "Remove older channel prereleases")
        assertFalse("Publication must not start Gradle", publication.contains("gradlew"))
    }

    private fun stepBody(workflow: String, name: String, nextName: String): String {
        val marker = "      - name: $name"
        val start = workflow.indexOf(marker)
        require(start >= 0) { "Missing workflow step: $name" }
        val end = workflow.indexOf("      - name: $nextName", start)
        require(end > start) { "Missing workflow step after $name: $nextName" }
        return workflow.substring(start, end)
    }

    private fun buildTypeBlock(gradle: String, name: String): String {
        val header = "$name {"
        val buildTypesStart = gradle.indexOf("buildTypes {")
        require(buildTypesStart >= 0) { "Missing buildTypes block" }
        val start = gradle.indexOf(header, buildTypesStart)
        require(start >= 0) { "Missing $name build type" }
        val openingBrace = gradle.indexOf('{', start)
        var depth = 0
        for (index in openingBrace until gradle.length) {
            when (gradle[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return gradle.substring(start, index + 1)
                }
            }
        }
        error("Unclosed $name build type")
    }
}
