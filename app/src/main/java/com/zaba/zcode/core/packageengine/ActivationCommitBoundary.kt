package com.zaba.zcode.core.packageengine

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Pure filesystem commit boundary used by [TransactionManager].
 *
 * Before [commitState] returns, every promoted directory is unreferenced and may
 * be removed on failure. After it returns, callers must never invoke rollback;
 * cleanup, logging, and journaling belong to [runBestEffort] instead.
 */
internal object ActivationCommitBoundary {
    data class Generation(
        val incoming: File,
        val finalDir: File,
    )

    fun promoteAndCommit(
        generations: List<Generation>,
        commitState: () -> Unit,
    ): Result<Unit> {
        val promoted = mutableListOf<Generation>()
        return try {
            for (generation in generations) {
                Files.move(
                    generation.incoming.toPath(),
                    generation.finalDir.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                )
                promoted += generation
            }
            commitState()
            Result.success(Unit)
        } catch (error: Exception) {
            // State was not committed: only the new, still-unreferenced
            // generations are eligible for rollback. Existing active paths are
            // never part of this list.
            generations.forEach { runCatching { it.incoming.deleteRecursively() } }
            promoted.forEach { runCatching { it.finalDir.deleteRecursively() } }
            Result.failure(error)
        }
    }

    /** Execute post-commit work independently so one failure cannot skip others. */
    fun runBestEffort(steps: List<Pair<String, () -> Unit>>): List<String> {
        val warnings = mutableListOf<String>()
        for ((label, step) in steps) {
            runCatching(step).onFailure { error ->
                warnings += "$label: ${error.message ?: error.javaClass.simpleName}"
            }
        }
        return warnings
    }
}
