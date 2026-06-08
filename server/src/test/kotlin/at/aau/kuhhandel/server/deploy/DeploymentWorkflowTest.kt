package at.aau.kuhhandel.server.deploy

import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertTrue

class DeploymentWorkflowTest {
    private val deploymentGateCommand =
        "./gradlew ktlintCheck :shared:test :server:test :app:testDebugUnitTest --stacktrace"
    private val workflows = listOf("CI.yml", "deploy-staging.yml", "deploy-production.yml")
    private val node20ActionRefs =
        listOf(
            "actions/checkout@v4",
            "actions/setup-java@v4",
            "gradle/actions/setup-gradle@v4",
            "android-actions/setup-android@v3",
            "actions/upload-artifact@v4",
            "docker/setup-buildx-action@v3",
            "docker/login-action@v3",
            "docker/build-push-action@v6",
        )

    @Test
    fun `deploy workflows run verification before pushing images`() {
        listOf("deploy-staging.yml", "deploy-production.yml").forEach { workflowName ->
            val workflow = workflow(workflowName)
            val testGateIndex = workflow.indexOf("Run deployment test gate")
            val imagePushIndex = workflow.indexOf("Build and push image")

            assertTrue(testGateIndex >= 0, "$workflowName is missing the deployment test gate")
            assertTrue(imagePushIndex >= 0, "$workflowName is missing the image build step")
            assertTrue(
                testGateIndex < imagePushIndex,
                "$workflowName must run the deployment test gate before pushing an image",
            )
            assertTrue(
                deploymentGateCommand in workflow,
                "$workflowName must run the same verification used by CI before deployment",
            )
        }
    }

    @Test
    fun `workflows do not use actions pinned to node 20 runtimes`() {
        workflows.forEach { workflowName ->
            val workflow = workflow(workflowName)

            node20ActionRefs.forEach { actionRef ->
                assertTrue(
                    actionRef !in workflow,
                    "$workflowName still uses $actionRef",
                )
            }
        }
    }

    private fun workflow(name: String): String =
        Path.of("..", ".github", "workflows", name).normalize().readText()
}
