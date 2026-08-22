package com.zaba.zcode.core.packageengine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ActivationCommitBoundaryTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun generation(name: String): ActivationCommitBoundary.Generation {
        val incoming = temporary.newFolder("incoming-$name")
        File(incoming, "payload.py").writeText("VALUE = '$name'\n")
        val finalDir = File(incoming.parentFile, "final-$name")
        return ActivationCommitBoundary.Generation(incoming, finalDir)
    }

    @Test
    fun commitFailureDeletesOnlyNewGenerationsAndLeavesOldActivePath() {
        val oldActive = temporary.newFolder("old-active")
        File(oldActive, "payload.py").writeText("VALUE = 'old'\n")
        val candidate = generation("new")

        val result = ActivationCommitBoundary.promoteAndCommit(listOf(candidate)) {
            throw IllegalStateException("injected state failure")
        }

        assertTrue(result.isFailure)
        assertTrue(oldActive.isDirectory)
        assertEquals("VALUE = 'old'\n", File(oldActive, "payload.py").readText())
        assertFalse(candidate.incoming.exists())
        assertFalse(candidate.finalDir.exists())
    }

    @Test
    fun postCommitCallbackFailureCannotDeleteCommittedGeneration() {
        val candidate = generation("committed")
        val state = temporary.newFile("installed.state")

        val commit = ActivationCommitBoundary.promoteAndCommit(listOf(candidate)) {
            state.writeText(candidate.finalDir.canonicalPath)
        }
        assertTrue(commit.isSuccess)

        var laterStepRan = false
        val warnings = ActivationCommitBoundary.runBestEffort(
            listOf(
                "onLog" to { throw IllegalStateException("injected log failure") },
                "journal" to { laterStepRan = true },
            )
        )

        assertEquals(1, warnings.size)
        assertTrue(warnings.single().startsWith("onLog:"))
        assertTrue(laterStepRan)
        assertEquals(candidate.finalDir.canonicalPath, state.readText())
        assertTrue(candidate.finalDir.isDirectory)
        assertTrue(File(candidate.finalDir, "payload.py").isFile)
    }
}
