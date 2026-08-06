package me.rerere.rikkahub.di

import kotlinx.serialization.json.Json
import me.rerere.highlight.Highlighter
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.AILoggingManager
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.data.ai.tools.local.BiometricResultBuffer
import me.rerere.rikkahub.data.ai.tools.local.CameraResultBuffer
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.ai.tools.local.InteractiveToolStreamer
import me.rerere.rikkahub.data.repository.ScheduledJobRepository
import me.rerere.rikkahub.data.repository.SshHostRepository
import me.rerere.rikkahub.data.repository.TelegramChatRepository
import me.rerere.rikkahub.data.notifications.NotificationListenerPreferences
import me.rerere.rikkahub.data.telegram.TelegramBotClient
import me.rerere.rikkahub.data.telegram.TelegramBotPreferences
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.CronJobScheduler
import me.rerere.rikkahub.utils.EmojiData
import me.rerere.rikkahub.utils.EmojiUtils
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.SoundEffectPlayer
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.web.WebServerManager
import me.rerere.tts.provider.TTSManager
import org.koin.dsl.module
import org.koin.core.qualifier.named

val appModule = module {
    single<Json> { JsonInstant }

    single {
        Highlighter(get())
    }

    single {
        AppEventBus()
    }

    single { me.rerere.rikkahub.data.ai.memory.AgentMemoryManager(get()) }
    single { me.rerere.rikkahub.data.ai.compressor.CompressedContentStore(java.io.File(get<android.content.Context>().filesDir, "compressed_outputs")) }
    single { CameraResultBuffer() }
    single { BiometricResultBuffer() }
    // Phase 25 — NFC reader-mode + SAF directory-picker Activity bridges, and the SAF
    // tree-grant store backing the ExternalStorage tools.
    single { me.rerere.rikkahub.data.ai.tools.local.NfcResultBuffer() }
    single { me.rerere.rikkahub.data.ai.tools.local.SafPickerResultBuffer() }
    single { me.rerere.rikkahub.data.storage.StorageVolumeGrantStore(get()) }

    single { ScheduledJobRepository(get<me.rerere.rikkahub.data.db.AppDatabase>().scheduledJobDao()) }
    single { me.rerere.rikkahub.data.repository.ScheduledJobRunRepository(get<me.rerere.rikkahub.data.db.AppDatabase>().scheduledJobRunDao()) }
    single { me.rerere.rikkahub.service.DirectModeActionRunner(get()) }
    single { CronJobScheduler(get(), get()) }
    single { SshHostRepository(get<me.rerere.rikkahub.data.db.AppDatabase>().sshHostDao()) }
    single { TelegramChatRepository(get<me.rerere.rikkahub.data.db.AppDatabase>().telegramChatDao()) }
    single { TelegramBotPreferences(get()) }
    single { me.rerere.rikkahub.browser.BrowserPreferences(get()) }
    single { me.rerere.rikkahub.data.preferences.TermuxPreferences(get()) }
    // Pass 3: Telegram-bound screenshot streamer for headless browser mode. Bound to the
    // [BrowserScreenshotStreamer] interface so [BrowserController.streamScreenshotIfHeadless]
    // can resolve it lazily via Koin without taking a constructor dep — avoids a cycle
    // through TelegramBotClient → TelegramBotPreferences → ... → LocalTools → controller.
    single<me.rerere.rikkahub.browser.BrowserScreenshotStreamer> {
        me.rerere.rikkahub.data.telegram.TelegramBrowserScreenshotStreamer(get(), get(), get())
    }
    // Interactive-tool post-action screenshot streamer for headless mode (Telegram bot /
    // cron / sub-agent). Resolves lazily inside each interactive tool's execute lambda so
    // there's no DI cycle through LocalTools → ChatService → ... → TelegramBotClient.
    single<InteractiveToolStreamer> {
        me.rerere.rikkahub.data.telegram.TelegramInteractiveToolStreamer(get(), get(), get(), get())
    }
    single { me.rerere.rikkahub.data.preferences.ToolApprovalPreferences(get()) }
    single { TelegramBotClient { runCatching { kotlinx.coroutines.runBlocking { get<TelegramBotPreferences>().current().token } }.getOrDefault("") } }
    // Phase 24 — Telegram long-poll stall tracker. Shared singleton: TelegramBotService's
    // poll loop calls markUpdate() on every getUpdates; the in-service stall checker and
    // DoctorChecks read it. No cross-dependencies, so no DI-cycle risk.
    single { me.rerere.rikkahub.data.telegram.TelegramPollStallTracker() }
    single { NotificationListenerPreferences(get()) }

    // Phase 13: External Automation Intent API
    single { me.rerere.rikkahub.automation.ExternalAutomationConfig(get()) }
    single {
        me.rerere.rikkahub.automation.ExternalAutomationDispatcher(
            context = get(),
            config = get(),
            chatService = get(),
            conversationRepo = get(),
            settingsStore = get(),
            appScope = get(),
            // Phase 24 — unified AgentRun ledger writer.
            agentRunRepo = get(),
        )
    }

    // Phase 14: Reliability bundle
    single { me.rerere.rikkahub.reliability.GitHubReleaseChecker(get()) }
    single { me.rerere.rikkahub.reliability.BugReportBuilder(get()) }

    // Phase 11: Sub-agents
    single { me.rerere.rikkahub.subagent.SubAgentRegistry() }
    single { me.rerere.rikkahub.skills.plugins.SlashCommandRegistry() }
    single {
        me.rerere.rikkahub.subagent.SubAgentEngine(
            registry = get(),
            // chatService is resolved lazily inside SubAgentEngine to break the
            // ChatService→LocalTools→SubAgentEngine→ChatService cycle. See SubAgentEngine kdoc.
            conversationRepo = get(),
            settingsStore = get(),
            appScope = get(),
            // Phase 24 — unified AgentRun ledger writer. No DI cycle: AgentRunRepository
            // depends only on its DAO.
            agentRunRepo = get(),
        )
    }

    // Phase 16: Skill URL-import
    single {
        me.rerere.rikkahub.skills.SkillUrlImporter(
            skillManager = get<me.rerere.rikkahub.data.files.SkillManager>(),
        )
    }

    single {
        me.rerere.rikkahub.skills.imports.ArtifactProvenanceStore(
            directory = java.io.File(get<android.content.Context>().filesDir, "import-provenance"),
            json = get(),
        )
    }
    single<me.rerere.rikkahub.skills.imports.ArtifactSourceAdapter>(named("skillImportAdapter")) {
        me.rerere.rikkahub.skills.imports.SkillUrlAdapter(get())
    }
    single<me.rerere.rikkahub.skills.imports.ArtifactSourceAdapter>(named("githubSkillImportAdapter")) {
        me.rerere.rikkahub.skills.imports.GitHubSkillAdapter(get(), get())
    }
    single<me.rerere.rikkahub.skills.imports.ArtifactSourceAdapter>(named("githubPluginImportAdapter")) {
        me.rerere.rikkahub.skills.imports.GitHubPluginAdapter(get(), get())
    }
    single {
        me.rerere.rikkahub.skills.imports.ImportCoordinator(
            adapters = listOf(
                get(named("githubPluginImportAdapter")),
                get(named("githubSkillImportAdapter")),
                get(named("skillImportAdapter")),
            ),
            provenanceStore = get(),
            installSkill = { candidate ->
                when (val payload = candidate.payload) {
                    is me.rerere.rikkahub.skills.imports.ImportPayload.SkillFiles ->
                        if (get<me.rerere.rikkahub.data.files.SkillManager>()
                                .saveSkillFilesAtomically(candidate.name, payload.files)
                        ) Result.success(me.rerere.rikkahub.skills.imports.InstalledArtifact(candidate.name))
                        else Result.failure(IllegalStateException("could not write skill files"))
                    is me.rerere.rikkahub.skills.imports.ImportPayload.SkillText ->
                        get<me.rerere.rikkahub.skills.SkillUrlImporter>()
                            .importFromText(payload.body, candidate.provenance.source)
                            .let { result ->
                                if (result is me.rerere.rikkahub.skills.SkillUrlImporter.Result.Ok) {
                                    Result.success(me.rerere.rikkahub.skills.imports.InstalledArtifact(candidate.name))
                                } else Result.failure(IllegalStateException(result.toString()))
                            }
                    else -> Result.failure(IllegalArgumentException("skill candidate has invalid payload"))
                }
            },
            installPlugin = { candidate ->
                val payload = candidate.payload as me.rerere.rikkahub.skills.imports.ImportPayload.PluginArchive
                get<me.rerere.rikkahub.skills.plugins.PluginManager>()
                    .installFromPreparedArchive(payload.file)
                    .map { installed -> me.rerere.rikkahub.skills.imports.InstalledArtifact(installed.name) }
            },
        )
    }

    single<me.rerere.rikkahub.data.catalog.ArtifactCatalogProvider> {
        me.rerere.rikkahub.data.catalog.BundledCatalogAdapter()
    }

    single<me.rerere.rikkahub.data.share.SharedPayloadStore> {
        me.rerere.rikkahub.data.share.InMemorySharedPayloadStore()
    }

    // Phase 19B: Skill isolation tester. Eager construction is safe here — ChatService
    // doesn't reach back into SkillTestRunner anywhere, so no DI cycle.
    single {
        me.rerere.rikkahub.skills.SkillTestRunner(
            chatService = get(),
            skillManager = get(),
            conversationRepo = get(),
            settingsStore = get(),
        )
    }

    // Phase 18: JS skills (run_js + secrets store)
    single { me.rerere.rikkahub.skills.js.JsSkillRunner(get()) }
    single { me.rerere.rikkahub.skills.js.SkillSecretsStore(get()) }

    // Skill System Enhancement
    single { me.rerere.rikkahub.skills.SkillUrlResolver(get()) }
    single { me.rerere.rikkahub.skills.SystemPromptFormatter() }

    // Phase 12: Workflows
    single {
        me.rerere.rikkahub.workflow.repository.WorkflowRepository(
            workflowDao = get<me.rerere.rikkahub.data.db.AppDatabase>().workflowDao(),
            workflowRunDao = get<me.rerere.rikkahub.data.db.AppDatabase>().workflowRunDao(),
        )
    }
    single { me.rerere.rikkahub.workflow.condition.ContextProvider(get()) }
    single { me.rerere.rikkahub.workflow.execution.WorkflowActionRunner() }
    single {
        me.rerere.rikkahub.workflow.execution.WorkflowEngine(
            repository = get(),
            settingsStore = get(),
            contextProvider = get(),
            actionRunner = get(),
        ).also { engine ->
            // Bridge for the repo to notify the engine on delete so the engine's per-workflow
            // lock map doesn't leak. Lazy because both singletons have to exist first.
            get<me.rerere.rikkahub.workflow.repository.WorkflowRepository>().bindEngine(engine)
        }
    }
    single {
        me.rerere.rikkahub.workflow.trigger.TriggerRegistry(
            context = get(),
            appScope = get(),
            workflowRepository = get(),
        )
    }

    single { me.rerere.rikkahub.data.keyboard.KeyboardApiClient(get()) }

    single {
        LocalTools(
            context = get(),
            eventBus = get(),
            cameraResultBuffer = get(),
            biometricResultBuffer = get(),
            scheduledJobRepository = get(),
            scheduledJobRunRepository = get(),
            cronJobScheduler = get(),
            settingsStore = get(),
            sshHostRepository = get(),
            telegramBotPreferences = get(),
            telegramBotClient = get(),
            notificationListenerPreferences = get(),
            mcpManager = get(),
            externalAutomationConfig = get(),
            gitHubReleaseChecker = get(),
            bugReportBuilder = get(),
            subAgentEngine = get(),
            subAgentRegistry = get(),
            conversationRepo = get(),
            workflowRepository = get(),
            workflowEngine = get(),
            skillUrlImporter = get(),
            skillManager = get(),
            jsSkillRunner = get(),
            skillSecretsStore = get(),
            browserPreferences = get(),
            termuxPreferences = get(),
            interactiveToolStreamer = get(),
            nfcResultBuffer = get(),
            safPickerResultBuffer = get(),
            storageVolumeGrantStore = get(),
            okHttpClient = get(),
            keyboardApiClient = get(),
            imageTools = get(),
            androidShareService = get(),
        )
    }

    single {
        me.rerere.rikkahub.data.share.ShareArtifactResolver(get(), get(), get())
    }

    single {
        me.rerere.rikkahub.data.share.AndroidShareService(get(), get())
    }

    single {
        UpdateChecker(get())
    }

    single {
        AppScope()
    }

    single<EmojiData> {
        EmojiUtils.loadEmoji(get())
    }

    single {
        TTSManager(get())
    }

    single {
        SoundEffectPlayer(get())
    }

    single {
        AILoggingManager(get(), get())
    }

    // Phase 22A: Local-LLM on-device providers
    single { me.rerere.locallm.LocalRuntimePreferences(get()) }
    single { me.rerere.locallm.litert.LiteRtRuntime(get()) }
    single<me.rerere.rikkahub.data.modelregistry.ModelRegistry> {
        me.rerere.rikkahub.data.modelregistry.SettingsModelRegistry(get(), get(), get<me.rerere.rikkahub.AppScope>(), get())
    }
    single { me.rerere.rikkahub.data.modelregistry.ModelRoleResolver(get()) }
    single { me.rerere.rikkahub.data.ai.tools.image.ProviderImageToolBackend(get()) }
    single { me.rerere.rikkahub.data.ai.tools.image.ImageTextExtractor(get(), get()) }
    single<me.rerere.rikkahub.data.media.ImageMediaStore> { me.rerere.rikkahub.data.media.DefaultImageMediaStore(get(), get()) }
    single<me.rerere.rikkahub.data.ai.tools.image.MediaInputResolver> { me.rerere.rikkahub.data.ai.tools.image.DefaultMediaInputResolver(get(), get(), get()) }
    single {
        me.rerere.rikkahub.data.ai.tools.image.ImageTools(
            settingsStore = me.rerere.rikkahub.data.ai.tools.image.SettingsProvider { get<me.rerere.rikkahub.data.datastore.SettingsStore>().settingsFlow.value },
            modelRoleResolver = get(),
            imageToolBackend = get(),
            imageMediaStore = get(),
            mediaInputResolver = get(),
            imageTextExtractor = get(),
        )
    }
    single<me.rerere.rikkahub.data.modelregistry.LegacyModelAssignmentAdapter> {
        me.rerere.rikkahub.data.modelregistry.SettingsLegacyModelAssignmentAdapter(get(), get<me.rerere.rikkahub.AppScope>())
    }

    single {
        ChatService(
            context = get(),
            appScope = get(),
            appEventBus = get(),
            settingsStore = get(),
            conversationRepo = get(),
            memoryRepository = get(),
            generationHandler = get(),
            templateTransformer = get(),
            providerManager = get(),
            localTools = get(),
            mcpManager = get(),
            filesManager = get(),
            skillManager = get(),
            toolApprovalPreferences = get(),
            workspaceRepository = get(),
            folderRepository = get(),
            pluginManager = get(),
            agentMemoryManager = get(),
            contentCompressorStore = get(),
        )
    }

    single {
        WebServerManager(
            context = get(),
            appScope = get(),
            chatService = get(),
            conversationRepo = get(),
            folderRepo = get(),
            settingsStore = get(),
            filesManager = get()
        )
    }

    single {
        me.rerere.rikkahub.ui.pages.setting.doctor.DoctorChecks(
            context = get(),
            settingsStore = get(),
            telegramPrefs = get(),
            workflowRepository = get(),
            scheduledJobRepository = get(),
            scheduledJobRunRepository = get(),
            conversationRepository = get(),
            database = get(),
            // Pass 3: surface the browser write-tools-enabled INFO row + profile-dir AutoFix.
            browserPreferences = get(),
            // Phase 25: surface the SAF granted-directories live count.
            storageVolumeGrantStore = get(),
            // LiteRT accelerator status row in the Doctor: shows the persisted backend
            // decision so a silent GPU -> CPU fallback is visible.
            localRuntimePreferences = get(),
        )
    }
}
