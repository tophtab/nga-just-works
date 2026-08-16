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
    fun rootGradleAcceptsDebugDistributionNamesAndRejectsLegacyPreviewNames() {
        val gradle = File(repositoryRoot, "build.gradle").readText()

        assertTrue(gradle.contains("(?:-debug\\.[0-9]+)?"))
        assertFalse(gradle.contains("-preview"))
    }

    @Test
    fun checkoutKeepsMainHistoryAndUsesShallowTagsWithBloblessPartialClone() {
        val workflow = File(repositoryRoot, ".github/workflows/build.yml").readText()
        val checkout = stepBody(workflow, "Checkout project sources", "Derive release identity")
        val depthExpression = Regex(
            "fetch-depth:\\s*\\$\\{\\{\\s*startsWith\\(github\\.ref, 'refs/tags/'\\)\\s*&&\\s*(\\d+)\\s*\\|\\|\\s*(\\d+)\\s*\\}\\}",
        ).find(checkout)

        assertEquals(1, Regex("(?m)^\\s*uses: actions/checkout@v4\\s*$").findAll(checkout).count())
        requireNotNull(depthExpression) { "Checkout depth must branch on stable tag refs" }
        assertEquals("Stable tag checkout must fetch only the tagged commit", 1, depthExpression.groupValues[1].toInt())
        assertEquals("Main preview checkout must retain complete history", 0, depthExpression.groupValues[2].toInt())
        assertEquals(1, Regex("(?m)^\\s*filter: blob:none\\s*$").findAll(checkout).count())

        val identity = stepBody(workflow, "Derive release identity", "Setup Java")
        assertTrue(identity.contains("git tag --merged \"\$GITHUB_SHA\" --sort=-version:refname"))
    }

    @Test
    fun workflowDerivesVersionCodeFromSemanticBaseAndPreviewCommitDistance() {
        val workflow = File(repositoryRoot, ".github/workflows/build.yml").readText()
        val identity = stepBody(workflow, "Derive release identity", "Setup Java")
        val previewBranchMarker = "\n          else\n            stable_base=\"\""
        val previewBranchStart = identity.indexOf(previewBranchMarker)
        assertTrue("Missing preview identity branch", previewBranchStart >= 0)
        val branchEndMarker = "\n          fi\n\n          version_code="
        val branchEnd = identity.indexOf(branchEndMarker, previewBranchStart)
        assertTrue("Missing versionCode derivation after identity branches", branchEnd > previewBranchStart)
        val stableBranch = identity.substring(0, previewBranchStart)
        val previewBranch = identity.substring(previewBranchStart, branchEnd)
        val versionCodeExport = identity.substring(branchEnd)

        assertTrue(File(repositoryRoot, "scripts/derive_android_version_code.py").isFile)
        assertTrue(stableBranch.contains("version_base=\"\$GITHUB_REF_NAME\""))
        assertTrue(stableBranch.contains("build_slot=0"))
        assertFalse(stableBranch.contains("commit_distance="))
        assertTrue(previewBranch.contains("version_base=\"\$stable_base\""))
        assertTrue(
            previewBranch.contains(
                "git rev-list --first-parent --count \"\${stable_base}..\${GITHUB_SHA}\"",
            ),
        )
        assertTrue(previewBranch.contains("build_slot=\$((commit_distance + 1))"))
        assertFalse(previewBranch.contains("build_slot=0"))
        assertTrue(
            versionCodeExport.contains(
                "python3 scripts/derive_android_version_code.py \"\$version_base\" \"\$build_slot\"",
            ),
        )
        val derivation = versionCodeExport.indexOf("python3 scripts/derive_android_version_code.py")
        val export = versionCodeExport.indexOf("echo \"CI_VERSION_CODE=\$version_code\"")
        assertTrue("versionCode must be derived before it is exported", derivation >= 0 && export > derivation)
        assertFalse(identity.contains("4043 + GITHUB_RUN_NUMBER"))
        assertEquals(1, "derive_android_version_code.py".toRegex().findAll(identity).count())
    }

    @Test
    fun sharedSdkAndPublishedApkChecksStayPinnedToApi29And35() {
        val rootGradle = File(repositoryRoot, "build.gradle").readText()
        val appGradle = File(repositoryRoot, "nga_phone_base_3.0/build.gradle").readText()
        val workflow = File(repositoryRoot, ".github/workflows/build.yml").readText()
        val staging = stepBody(workflow, "Verify and stage APK", "Create GitHub Release")

        assertTrue(rootGradle.contains("minSdkVersion = 29"))
        assertTrue(rootGradle.contains("targetSdkVersion = 35"))
        assertTrue(rootGradle.contains("compileSdkVersion = 35"))
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
        assertTrue(staging.contains("test \"\$(apkanalyzer manifest target-sdk \"\$release_apk\")\" = \"35\""))
    }

    @Test
    fun mainPublishesDebugNamedPrereleaseAndTagsPublishStableRelease() {
        val workflow = File(repositoryRoot, ".github/workflows/build.yml").readText()

        assertTrue(workflow.contains("version_name=\"\${stable_base}-debug.\${GITHUB_RUN_NUMBER}\""))
        assertTrue(workflow.contains("release_tag=\"debug-\${short_sha}\""))
        assertTrue(workflow.contains("release_title=\"NGA Just Works \${version_name} (Debug)\""))
        assertTrue(workflow.contains("release_title=\"NGA Just Works \$GITHUB_REF_NAME\""))
        assertTrue(workflow.contains("gradle_tasks=\":nga_phone_base_3.0:assemblePreview\""))
        assertTrue(workflow.contains("apk_dir=preview"))
        assertTrue(workflow.contains("expected_debuggable=true"))

        assertTrue(workflow.contains("gradle_tasks=\"verifyReleaseTag :nga_phone_base_3.0:assembleRelease\""))
        assertTrue(workflow.contains("apk_dir=release"))
        assertTrue(workflow.contains("expected_debuggable=false"))
        assertTrue(workflow.contains("release_apk=\"dist/NGA-Just-Works-\${app_version}.apk\""))
        assertFalse(workflow.contains("NGA-Just-Works-\${app_version}-debug.apk"))
        assertTrue(workflow.contains("-F prerelease=true"))
        assertTrue(workflow.contains("--prerelease"))
    }

    @Test
    fun workflowVerifiesUpgradeIdentityAndCleansLegacyAndCurrentDebugTags() {
        val workflow = File(repositoryRoot, ".github/workflows/build.yml").readText()

        assertTrue(workflow.contains("manifest application-id"))
        assertTrue(workflow.contains("com.github.tophtab.ngajustworks"))
        assertTrue(workflow.contains("manifest version-name"))
        assertTrue(workflow.contains("manifest version-code"))
        assertTrue(workflow.contains("manifest debuggable"))
        assertTrue(workflow.contains("apksigner\" verify --verbose --print-certs"))
        assertTrue(workflow.contains("test \"\${#source_apks[@]}\" -eq 1"))
        assertTrue(workflow.contains("test \"\$(find dist -maxdepth 1 -type f | wc -l)\" -eq 2"))
        assertTrue(workflow.contains("sha256sum -c ./*.sha256"))
        assertTrue(workflow.contains("select(.prerelease == true"))
        assertTrue(workflow.contains("startswith(\"preview-\")"))
        assertTrue(workflow.contains("startswith(\"debug-\")"))
        assertTrue(workflow.contains("--cleanup-tag"))
        assertTrue(workflow.contains("old_tag\" != \"\$CURRENT_DEBUG_TAG"))
    }

    @Test
    fun stableReleaseUsesValidatedVersionedNotesWhileDebugKeepsGeneratedNotes() {
        val workflow = File(repositoryRoot, ".github/workflows/build.yml").readText()
        val publication = workflow.substringAfter("      - name: Create GitHub Release")
            .substringBefore("      - name: Remove older debug prereleases")
        val stableBranchMarker =
            "\n          else\n            release_notes=\"release-notes/\${GITHUB_REF_NAME}.md\""
        val stableBranchStart = publication.indexOf(stableBranchMarker)
        assertTrue("Missing stable publication branch", stableBranchStart >= 0)
        val debugPublication = publication.substring(0, stableBranchStart)
        val stablePublication = publication.substring(stableBranchStart)

        assertTrue(File(repositoryRoot, "release-notes/4.9.0.md").isFile)
        assertTrue(File(repositoryRoot, "release-notes/4.10.0.md").isFile)
        assertTrue(File(repositoryRoot, "scripts/validate_release_notes.py").isFile)
        assertTrue(debugPublication.contains("--generate-notes"))
        assertFalse(debugPublication.contains("--notes-file"))
        assertTrue(stablePublication.contains("release_notes=\"release-notes/\${GITHUB_REF_NAME}.md\""))
        assertTrue(stablePublication.contains("python3 scripts/validate_release_notes.py \"\$release_notes\""))
        assertTrue(stablePublication.contains("--notes-file \"\$release_notes\""))
        assertFalse(stablePublication.contains("--generate-notes"))
        val validation = stablePublication.indexOf("python3 scripts/validate_release_notes.py")
        val releaseCreation = stablePublication.indexOf("gh release create")
        assertTrue("Stable notes must be validated before release creation", validation >= 0)
        assertTrue("Stable notes must be validated before release creation", releaseCreation > validation)
        assertEquals(1, "--generate-notes".toRegex().findAll(publication).count())
        assertEquals(1, "--notes-file".toRegex().findAll(publication).count())
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

        val publication = stepBody(workflow, "Create GitHub Release", "Remove older debug prereleases")
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
