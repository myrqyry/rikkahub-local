package me.rerere.rikkahub.di

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.first
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.rag.EmbeddingBackend
import me.rerere.rikkahub.data.rag.EmbeddingRepository
import me.rerere.rikkahub.data.rag.ProviderEmbeddingBackend
import me.rerere.rikkahub.data.rag.QwenEmbeddingBackend
import me.rerere.rikkahub.data.rag.RagEmbeddingSource
import me.rerere.rikkahub.data.rag.resolveRagEmbeddingSource
import me.rerere.reranker.QwenEngineRegistry
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.FilesRepository
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.pages.setting.components.QwenSemanticModelManager
import me.rerere.workspace.ProotShellRunner
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceBindMount
import me.rerere.workspace.WorkspaceManager
import org.koin.dsl.module

val repositoryModule = module {
    single {
        ConversationRepository(get(), get(), get(), get(), get(), get())
    }

    single {
        FolderRepository(get(), get())
    }

    single {
        MemoryRepository(get())
    }

    single {
        GenMediaRepository(get())
    }

    single {
        FilesRepository(get())
    }

    single {
        FavoriteRepository(get())
    }

    single {
        val context: Context = get()
        WorkspaceManager(
            baseDir = File(context.filesDir, "workspaces"),
            shellRunner = ProotShellRunner(
                nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir),
                extraBindMounts = listOf(
                    WorkspaceBindMount(
                        source = File(context.filesDir, FileFolders.SKILLS).apply { mkdirs() },
                        target = "/skills",
                    ),
                    WorkspaceBindMount(
                        source = File(context.filesDir, FileFolders.TOOL_OUTPUTS).apply { mkdirs() },
                        target = "/tool_outputs",
                    ),
                    WorkspaceBindMount(
                        source = File(context.filesDir, FileFolders.UPLOAD).apply { mkdirs() },
                        target = "/upload",
                    ),
                ),
            )
        )
    }

    single {
        RootfsInstaller(get())
    }

    single {
        WorkspaceRepository(get(), get(), get(), get())
    }

    single {
        FilesManager(get(), get(), get())
    }

    single {
        SkillManager(get(), get())
    }

    single {
        val context: Context = get()
        val settingsStore: SettingsStore = get()
        val embedderDir = File(context.filesDir, "models/embedder")
        EmbeddingRepository(
            backendProvider = {
                val settings = settingsStore.settingsFlow.first()
                val localReady = QwenSemanticModelManager.validate(
                    embedderDir,
                    QwenSemanticModelManager.ModelKind.Embedder,
                ) is QwenSemanticModelManager.ModelStatus.Ready
                when (val source = resolveRagEmbeddingSource(settings, embedderDir, localReady)) {
                    is RagEmbeddingSource.LocalQwen -> QwenEmbeddingBackend(source.modelDir)
                    is RagEmbeddingSource.Provider ->
                        ProviderEmbeddingBackend(get(), source.providerSetting, source.model)
                    null -> null
                }
            },
            vectorDao = get(),
            json = get(),
            rerankerProvider = {
                QwenEngineRegistry.reranker(File(context.filesDir, "models/reranker"))
            },
        )
    }
}
