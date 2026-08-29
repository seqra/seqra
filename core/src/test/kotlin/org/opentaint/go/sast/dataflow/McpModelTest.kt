package org.opentaint.go.sast.dataflow

import org.opentaint.common.sast.CommonAnalysisOptions
import org.opentaint.dataflow.configuration.go.serialized.GoNameMatcher
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedAssignAction
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedCondition
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedRule
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedTaintConfig
import org.opentaint.dataflow.configuration.go.serialized.GoSinkMetaData
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Result
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier
import org.opentaint.dataflow.go.rules.GoTaintConfiguration
import org.opentaint.go.config.GoDefaultConfigLoader
import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.client.GoIRClient
import org.opentaint.ir.go.client.GoIRLoadConfig
import org.opentaint.ir.go.client.GoIRLoadMode
import org.opentaint.ir.go.ext.findFunctionByFullName
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class McpModelTest {
    @Test
    fun `MCP model connects registration to tool handler`() {
        val project = Files.createTempDirectory("go-mcp-model")
        writeProjectModule(project)
        project.resolve("mcp.go").writeText(
            """
            package mcpapp

            import (
                "context"
                "github.com/modelcontextprotocol/go-sdk/mcp"
            )

            func Source() string { return "" }
            func Sink(string) {}

            func toolHandler(context.Context, *mcp.CallToolRequest) (*mcp.CallToolResult, error) {
                Sink(Source())
                return nil, nil
            }

            func Register(server *mcp.Server) {
                server.AddTool(
                    &mcp.Tool{Name: "test", InputSchema: map[string]any{"type": "object"}},
                    toolHandler,
                )
            }
            """.trimIndent(),
        )
        assertModelConnects(project)
    }

    @Test
    fun `MCP model connects server options to completion handler`() {
        val project = Files.createTempDirectory("go-mcp-options-model")
        writeProjectModule(project)
        project.resolve("mcp.go").writeText(
            """
            package mcpapp

            import (
                "context"
                "github.com/modelcontextprotocol/go-sdk/mcp"
            )

            func Source() string { return "" }
            func Sink(string) {}

            func completionHandler(context.Context, *mcp.CompleteRequest) (*mcp.CompleteResult, error) {
                Sink(Source())
                return nil, nil
            }

            func Register() {
                mcp.NewServer(
                    &mcp.Implementation{},
                    &mcp.ServerOptions{CompletionHandler: completionHandler},
                )
            }
            """.trimIndent(),
        )

        assertModelConnects(project)
    }

    @Test
    fun `MCP callback model and pass-through rule work together`() {
        val project = Files.createTempDirectory("go-mcp-pass-through-model")
        writeProjectModule(project)
        project.resolve("mcp.go").writeText(
            """
            package mcpapp

            import (
                "context"
                "github.com/modelcontextprotocol/go-sdk/mcp"
            )

            func Source() string { return "" }
            func Sink(error) {}

            func toolHandler(context.Context, *mcp.CallToolRequest) (*mcp.CallToolResult, error) {
                Sink(mcp.ResourceNotFoundError(Source()))
                return nil, nil
            }

            func Register(server *mcp.Server) {
                server.AddTool(&mcp.Tool{}, toolHandler)
            }
            """.trimIndent(),
        )
        val passThrough = GoDefaultConfigLoader.loadConfig()?.passThrough.orEmpty()
        assertTrue(
            passThrough.any {
                it.function == GoNameMatcher.Simple("ResourceNotFoundError") &&
                    it.pkg == GoNameMatcher.Simple("github.com/modelcontextprotocol/go-sdk/mcp")
            },
        )

        GoIRClient().use { client ->
            val withoutModel = client.buildFromDir(project).program
            val withModel = client.buildFromDir(
                project,
                GoIRLoadConfig(modelDirs = listOf(modelPath())),
            ).program

            assertTrue(findVulnerabilities(withoutModel, passThrough).isEmpty())
            assertTrue(findVulnerabilities(withModel).isEmpty())
            assertTrue(findVulnerabilities(withModel, passThrough).isNotEmpty())
        }
    }

    @Test
    fun `MCP tool input is an untrusted callback value`() {
        val project = Files.createTempDirectory("go-mcp-input-model")
        writeProjectModule(project)
        project.resolve("mcp.go").writeText(
            """
            package mcpapp

            import (
                "context"
                "github.com/modelcontextprotocol/go-sdk/mcp"
            )

            func Sink(string) {}

            func toolHandler(
                context.Context,
                *mcp.CallToolRequest,
                map[string]string,
            ) (*mcp.CallToolResult, any, error) {
                return nil, nil, nil
            }

            func Register(server *mcp.Server) {
                mcp.AddTool(
                    server,
                    &mcp.Tool{Name: "test"},
                    func(
                        ctx context.Context,
                        req *mcp.CallToolRequest,
                        input map[string]string,
                    ) (*mcp.CallToolResult, any, error) {
                        Sink(input["command"])
                        return toolHandler(ctx, req, input)
                    },
                )
            }
            """.trimIndent(),
        )

        val passThrough = GoDefaultConfigLoader.loadConfig()?.passThrough.orEmpty()
        GoIRClient().use { client ->
            val withoutModel = client.buildFromDir(project).program
            val withModel = client.buildFromDir(
                project,
                GoIRLoadConfig(modelDirs = listOf(modelPath())),
            ).program

            assertTrue(findVulnerabilities(withoutModel, passThrough).isEmpty())
            assertTrue(findVulnerabilities(withModel, passThrough).isNotEmpty())
        }
    }

    @Test
    fun `MCP callback and pass-through model reach a GitHub workflow mutation`() {
        val project = Files.createTempDirectory("go-mcp-github-mutation")
        writeProjectModule(project)
        writeGoGitHubStub(project)
        project.resolve("mcp.go").writeText(
            """
            package mcpapp

            import (
                "context"
                "strconv"
                "github.com/google/go-github/v89/github"
                "github.com/modelcontextprotocol/go-sdk/mcp"
            )

            func Register(server *mcp.Server, client *github.Client) {
                mcp.AddTool(
                    server,
                    &mcp.Tool{Name: "run_workflow"},
                    func(
                        ctx context.Context,
                        _ *mcp.CallToolRequest,
                        input map[string]string,
                    ) (*mcp.CallToolResult, any, error) {
                        workflowIDText := github.Ptr(input["workflow_id"])
                        workflowID, err := strconv.ParseInt(*workflowIDText, 10, 64)
                        if err != nil {
                            return nil, nil, err
                        }
                        _, err = client.Actions.CreateWorkflowDispatchEventByID(
                            ctx,
                            "example",
                            "repository",
                            workflowID,
                            github.CreateWorkflowDispatchEventRequest{},
                        )
                        return nil, nil, err
                    },
                )
            }
            """.trimIndent(),
        )

        val passThrough = GoDefaultConfigLoader.loadConfig()?.passThrough.orEmpty()
        val pointerRules = passThrough.filter {
            it.pkg == GoNameMatcher.Simple("github.com/google/go-github/v89/github") &&
                it.function == GoNameMatcher.Simple("Ptr")
        }
        val parseRules = passThrough.filter {
            it.pkg == GoNameMatcher.Simple("strconv") &&
                it.function == GoNameMatcher.Simple("ParseInt")
        }
        assertTrue(pointerRules.isNotEmpty())
        assertTrue(parseRules.isNotEmpty())

        GoIRClient().use { client ->
            val program = client.buildFromDir(
                project,
                GoIRLoadConfig(modelDirs = listOf(modelPath())),
            ).program
            assertTrue(findGitHubMutations(program).isEmpty())
            assertTrue(findGitHubMutations(program, pointerRules).isEmpty())
            assertTrue(findGitHubMutations(program, parseRules).isEmpty())
            assertTrue(findGitHubMutations(program, pointerRules + parseRules).isNotEmpty())
        }
    }

    @Test
    fun `GitHub MCP callback reaches a real workflow mutation`() {
        val checkout = System.getenv("GITHUB_MCP_SERVER_DIR")
        assumeTrue(checkout != null, "GITHUB_MCP_SERVER_DIR is not set")
        checkNotNull(checkout)

        GoIRClient().use { client ->
            val program = client.buildFromDir(
                Path.of(checkout),
                GoIRLoadConfig(
                    mode = GoIRLoadMode.PROJECT,
                    patterns = listOf("./..."),
                    modelDirs = listOf(modelPath(), githubMcpModelPath()),
                ),
            ).program
            val entryPoint = program
                .findPackage("github.com/github/github-mcp-server/pkg/github")!!
                .functions.single { it.name == "ActionsRunTrigger" }
            val inventory = program.findPackage("github.com/github/github-mcp-server/pkg/inventory")!!
            assertTrue(inventory.findNamedType("ToolsetMetadata")!!.fields.any { it.name == "ID" })
            assertTrue(inventory.findNamedType("ServerTool")!!.fields.any { it.name == "HandlerFunc" })

            val requiredPassThrough = GoDefaultConfigLoader.loadConfig()?.passThrough.orEmpty().filter {
                it.pkg == GoNameMatcher.Simple("strconv") &&
                    it.function == GoNameMatcher.Simple("ParseInt")
            }
            assertTrue(requiredPassThrough.isNotEmpty())
            val withoutAdapter = client.buildFromDir(
                Path.of(checkout),
                GoIRLoadConfig(
                    mode = GoIRLoadMode.PROJECT,
                    patterns = listOf("./..."),
                    modelDirs = listOf(modelPath()),
                ),
            ).program
            assertTrue(
                findVulnerabilities(
                    program = withoutAdapter,
                    passThrough = requiredPassThrough,
                    entryPoint = entryPoint.fullName,
                    sink = githubMutationSink("CreateWorkflowDispatchEventByID", Argument(3)),
                    timeout = 10.minutes,
                ).isEmpty(),
            )
            assertTrue(
                findVulnerabilities(
                    program = program,
                    entryPoint = entryPoint.fullName,
                    sink = githubMutationSink("CreateWorkflowDispatchEventByID", Argument(3)),
                    timeout = 10.minutes,
                ).isEmpty(),
            )
            assertTrue(
                findVulnerabilities(
                    program = program,
                    passThrough = requiredPassThrough,
                    entryPoint = entryPoint.fullName,
                    sink = githubMutationSink("CreateWorkflowDispatchEventByID", Argument(3)),
                    timeout = 10.minutes,
                ).isNotEmpty(),
            )
        }
    }

    private fun assertModelConnects(project: Path) {
        GoIRClient().use { client ->
            val withoutModel = client.buildFromDir(project).program
            val withModel = client.buildFromDir(
                project,
                GoIRLoadConfig(modelDirs = listOf(modelPath())),
            ).program

            assertTrue(findVulnerabilities(withoutModel).isEmpty())
            assertTrue(findVulnerabilities(withModel).isNotEmpty())
        }
    }

    private fun modelPath(): Path = Path.of(System.getProperty("opentaint.go.models.root"))
        .resolve("modelcontextprotocol-go-sdk-v1")

    private fun githubMcpModelPath(): Path = Path.of(System.getProperty("opentaint.go.models.root"))
        .resolve("github-mcp-server")

    private fun writeProjectModule(project: Path) {
        val goMod = Files.readString(modelPath().resolve("go.mod"))
            .replaceFirst("module opentaint", "module example.com/mcpapp")
        project.resolve("go.mod").writeText(goMod)
        Files.copy(modelPath().resolve("go.sum"), project.resolve("go.sum"))
    }

    private fun writeGoGitHubStub(project: Path) {
        project.resolve("go.mod").writeText(
            Files.readString(project.resolve("go.mod")) +
                "\nrequire github.com/google/go-github/v89 v89.0.0\n" +
                "replace github.com/google/go-github/v89 => ./go-github\n",
        )
        val module = project.resolve("go-github")
        val pkg = module.resolve("github")
        Files.createDirectories(pkg)
        module.resolve("go.mod").writeText(
            "module github.com/google/go-github/v89\n\ngo 1.25\n",
        )
        pkg.resolve("github.go").writeText(
            """
            package github

            import "context"

            type CreateWorkflowDispatchEventRequest struct{}
            type ActionsService struct{}
            type Client struct { Actions *ActionsService }

            func Ptr[T any](value T) *T { return &value }

            func (*ActionsService) CreateWorkflowDispatchEventByID(
                context.Context,
                string,
                string,
                int64,
                CreateWorkflowDispatchEventRequest,
            ) (bool, error) {
                return false, nil
            }
            """.trimIndent(),
        )
    }

    private fun findVulnerabilities(
        program: GoIRProgram,
        passThrough: List<GoSerializedRule.PassThrough> = emptyList(),
        entryPoint: String = "example.com/mcpapp.Register",
        sink: GoSerializedRule.Sink = localSink(),
        timeout: kotlin.time.Duration = 1.minutes,
    ) = GoTaintAnalyzer(
        program,
        rules(passThrough, sink),
        GoUnitResolver(),
        CommonAnalysisOptions(ifdsAnalysisTimeout = timeout).taintAnalyzerOptions(),
    ).use { analyzer ->
        val entryPointFunction = program.findFunctionByFullName(entryPoint)
            ?: error("MCP test entry point is missing")
        analyzer.analyzeWithIfds(listOf(entryPointFunction)).first
    }

    private fun findGitHubMutations(
        program: GoIRProgram,
        passThrough: List<GoSerializedRule.PassThrough> = emptyList(),
    ) = findVulnerabilities(
        program = program,
        passThrough = passThrough,
        sink = githubMutationSink("CreateWorkflowDispatchEventByID", Argument(3)),
    )

    private fun rules(
        passThrough: List<GoSerializedRule.PassThrough>,
        sink: GoSerializedRule.Sink,
    ): GoTaintConfiguration {
        fun source(
            pkg: GoNameMatcher,
            function: GoNameMatcher,
        ) = GoSerializedRule.Source(
            pkg = pkg,
            function = function,
            condition = null,
            taint = listOf(
                GoSerializedAssignAction(
                    "taint",
                    PositionBaseWithModifiers.WithModifiers(Result, listOf(PositionModifier.AnyField)),
                ),
            ),
            info = null,
        )
        return GoTaintConfiguration().also {
            it.loadConfig(
                GoSerializedTaintConfig(
                    source = listOf(
                        source(GoNameMatcher.Simple("example.com/mcpapp"), GoNameMatcher.Simple("Source")),
                        source(
                            GoNameMatcher.Pattern(
                                "(?:github.com/modelcontextprotocol/go-sdk/mcp|" +
                                    "github.com/github/github-mcp-server/pkg/inventory)",
                            ),
                            GoNameMatcher.Pattern("opentaintMCPIncoming(?:\\[.*])?"),
                        ),
                    ),
                    sink = listOf(sink),
                    passThrough = passThrough,
                ),
            )
        }
    }

    private fun localSink() = GoSerializedRule.Sink(
        pkg = GoNameMatcher.Simple("example.com/mcpapp"),
        function = GoNameMatcher.Simple("Sink"),
        condition = GoSerializedCondition.ContainsMark(
            "taint",
            PositionBaseWithModifiers.BaseOnly(Argument(0)),
        ),
        trackFactsReachAnalysisEnd = emptyList(),
        id = "mcp-model-test",
        meta = GoSinkMetaData("MCP model test sink"),
        info = null,
    )

    private fun githubMutationSink(
        function: String,
        position: Argument,
    ) = GoSerializedRule.Sink(
        pkg = GoNameMatcher.Simple("(*github.com/google/go-github/v89/github.ActionsService)"),
        function = GoNameMatcher.Simple(function),
        condition = GoSerializedCondition.ContainsMark(
            "taint",
            PositionBaseWithModifiers.WithModifiers(position, listOf(PositionModifier.AnyField)),
        ),
        trackFactsReachAnalysisEnd = emptyList(),
        id = "mcp-github-privileged-action-test",
        meta = GoSinkMetaData("MCP input reaches a GitHub mutation"),
        info = null,
    )
}
