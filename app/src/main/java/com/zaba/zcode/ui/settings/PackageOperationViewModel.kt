package com.zaba.zcode.ui.settings

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zaba.zcode.core.logging.SemanticLogKind
import com.zaba.zcode.core.packageengine.DependencyResolver
import com.zaba.zcode.core.packageengine.PackageEngineV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * Activity-scoped owner for package engine work.
 *
 * PipScreen may leave composition while Chaquopy/network work is still blocking.
 * Keeping the engine, coroutine, console, immutable requirement snapshot, and
 * cancellation handle here prevents a second screen instance from creating an
 * unreachable engine while the first one still owns PackageEngineV2.busyFlag.
 */
class PackageOperationViewModel(app: Application) : AndroidViewModel(app) {
    enum class OwnerState { IDLE, RUNNING, CANCELLING }

    val engine = PackageEngineV2(app.applicationContext)
    private val operationMutex = Mutex()
    private val nextOperationId = AtomicLong(0)
    private var activeJob: Job? = null

    val ownerState = mutableStateOf(OwnerState.IDLE)
    val operationId = mutableStateOf(0L)
    val packageEnvironmentRevision = mutableStateOf(0L)
    val activeTab = mutableStateOf(PipTab.LIBRARY)

    val packageName = mutableStateOf("")
    val activeRequirement = mutableStateOf<String?>(null)
    val isInstalling = mutableStateOf(false)
    val isAnalyzing = mutableStateOf(false)
    val isCancelling = mutableStateOf(false)
    val consoleLines = mutableStateOf(
        listOf(
            ConsoleLine("ZCODE Package Engine V2 — Chaquopy 3.11", SemanticLogKind.STEP),
            ConsoleLine("Masukkan requirement (bukan perintah shell), lalu tap Install.", SemanticLogKind.INFO),
            ConsoleLine("Instalasi transaksional: verifikasi + smoke test + rollback otomatis.", SemanticLogKind.INFO),
            ConsoleLine("Flow: Parse → Resolve → Download → Verify → Extract → Smoke → Activate", SemanticLogKind.INFO),
        )
    )
    val installQueue = mutableStateOf(listOf<String>())
    val pendingRiskyReq = mutableStateOf<String?>(null)
    val pendingRiskyReason = mutableStateOf("")
    val pendingRiskyPlan = mutableStateOf<DependencyResolver.ResolvePlan?>(null)

    /** Serialize analyze/install/uninstall under one owner. */
    fun launchOperation(block: suspend (PackageEngineV2) -> Unit): Job {
        val id = nextOperationId.incrementAndGet()
        operationId.value = id
        ownerState.value = OwnerState.RUNNING
        val job = viewModelScope.launch(Dispatchers.Default) {
            operationMutex.withLock {
                block(engine)
            }
        }
        activeJob = job
        job.invokeOnCompletion {
            postToMain {
                if (operationId.value == id) {
                    ownerState.value = OwnerState.IDLE
                    isCancelling.value = false
                    activeJob = null
                }
            }
        }
        return job
    }

    fun postToMain(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.Main.immediate) { block() }
    }

    fun markCancelling() {
        ownerState.value = OwnerState.CANCELLING
        isCancelling.value = true
    }

    fun markEnvironmentChanged() {
        packageEnvironmentRevision.value += 1L
    }

    override fun onCleared() {
        engine.cancelCurrentOperation()
        engine.requestInstallCancel()
        activeJob?.cancel()
        super.onCleared()
    }
}
