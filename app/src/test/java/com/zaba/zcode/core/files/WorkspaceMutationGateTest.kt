package com.zaba.zcode.core.files

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceMutationGateTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun clearWaitsForAcceptedWriteThenDeletesItSoFileCannotResurrect() {
        val gate = WorkspaceMutationGate()
        val file = File(temporary.root, "closed.py")
        file.writeText("old\n")
        var revision = 1L
        var opened = true
        val writeEntered = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val clearAttempting = CountDownLatch(1)
        val clearFinished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)

        val saveThread = Thread {
            runCatching {
                gate.writeIfCurrent(
                    isCurrent = { opened && revision == 1L },
                    write = {
                        writeEntered.countDown()
                        assertTrue(releaseWrite.await(5, TimeUnit.SECONDS))
                        file.writeText("stale\n")
                    },
                )
            }.onFailure(failure::set)
        }
        val clearThread = Thread {
            runCatching {
                assertTrue(writeEntered.await(5, TimeUnit.SECONDS))
                clearAttempting.countDown()
                gate.mutate {
                    opened = false
                    revision++
                    file.delete()
                }
                clearFinished.countDown()
            }.onFailure(failure::set)
        }

        saveThread.start()
        clearThread.start()
        assertTrue(writeEntered.await(5, TimeUnit.SECONDS))
        assertTrue(clearAttempting.await(5, TimeUnit.SECONDS))
        assertFalse("Clear must wait while accepted write owns the gate", clearFinished.await(100, TimeUnit.MILLISECONDS))
        releaseWrite.countDown()
        saveThread.join(5_000)
        clearThread.join(5_000)

        failure.get()?.let { throw AssertionError(it) }
        assertFalse(saveThread.isAlive)
        assertFalse(clearThread.isAlive)
        assertFalse("Clear commits after the accepted write, so no resurrection remains", file.exists())
    }

    @Test
    fun saveQueuedBeforeClearBecomesNoOpWhenClearCommitsFirst() {
        val gate = WorkspaceMutationGate()
        val file = File(temporary.root, "renamed.py")
        file.writeText("old\n")
        var revision = 7L
        var opened = true
        val queuedRevision = revision

        gate.mutate {
            opened = false
            revision++
            file.delete()
        }
        val wrote = gate.writeIfCurrent(
            isCurrent = { opened && revision == queuedRevision },
            write = { file.writeText("stale\n") },
        )

        assertFalse(wrote)
        assertFalse(file.exists())
    }
}
