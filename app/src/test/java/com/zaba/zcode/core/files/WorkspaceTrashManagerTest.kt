package com.zaba.zcode.core.files

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceTrashManagerTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun fixture(
        mover: ((File, File) -> Unit)? = null,
    ): Triple<File, File, WorkspaceTrashManager> {
        val root = temporary.newFolder()
        val workspace = File(root, "files").apply { mkdirs() }
        val trash = File(workspace, ".zcode-trash")
        return Triple(workspace, trash, WorkspaceTrashManager(workspace, trash, mover))
    }

    @Test
    fun clearMovesEveryPythonFileIncludingFilesNotRepresentedByTabs() {
        val (workspace, _, manager) = fixture()
        File(workspace, "open.py").writeText("open")
        File(workspace, "closed.py").writeText("closed")
        File(workspace, "notes.txt").writeText("keep")

        val result = manager.clearAll("{\"workspace\":\"old\"}")

        assertTrue(result.message, result.ok)
        assertEquals(2, result.count)
        assertFalse(File(workspace, "open.py").exists())
        assertFalse(File(workspace, "closed.py").exists())
        assertTrue(File(workspace, "notes.txt").isFile)
        assertTrue(manager.hasRestorableClear())
    }

    @Test
    fun failedMoveRollsBackAlreadyMovedFiles() {
        var moves = 0
        val mover: (File, File) -> Unit = { source, target ->
            moves++
            if (moves == 2) throw IOException("injected move failure")
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }
        val (workspace, _, manager) = fixture(mover)
        File(workspace, "a.py").writeText("a")
        File(workspace, "b.py").writeText("b")

        val result = manager.clearAll("{}")

        assertFalse(result.ok)
        assertEquals("a", File(workspace, "a.py").readText())
        assertEquals("b", File(workspace, "b.py").readText())
        assertFalse(manager.hasRestorableClear())
    }

    @Test
    fun restoreNeverOverwritesNewerConflictingFile() {
        val (workspace, _, manager) = fixture()
        File(workspace, "main.py").writeText("old user code")
        assertTrue(manager.clearAll("{\"active\":\"main.py\"}").ok)
        File(workspace, "main.py").writeText("new code after clear")

        val restored = manager.beginRestore()

        assertTrue(restored.message, restored.ok)
        assertEquals("new code after clear", File(workspace, "main.py").readText())
        val restoredName = restored.restoredNames.getValue("main.py")
        assertEquals("main_restored_2.py", restoredName)
        assertEquals("old user code", File(workspace, restoredName).readText())
        assertTrue(manager.hasRestorableClear())
        assertTrue(manager.finishRestore(restored))
        assertFalse(manager.hasRestorableClear())
    }

    @Test
    fun interruptedIncomingClearIsRecoveredOnNextStartup() {
        val (workspace, trash, manager) = fixture()
        val incoming = File(trash, ".incoming-crash").apply { mkdirs() }
        File(incoming, "closed.py").writeText("recover me")
        File(incoming, "workspace-state.json").writeText("{}")

        val message = manager.recoverInterruptedClear()

        assertTrue(message.orEmpty().contains("1 file"))
        assertEquals("recover me", File(workspace, "closed.py").readText())
        assertFalse(incoming.exists())
    }

    @Test
    fun rollbackRestoreDeletesOnlyCopiesAndKeepsCommittedTrash() {
        val (workspace, _, manager) = fixture()
        File(workspace, "keep.py").writeText("old")
        assertTrue(manager.clearAll("{}").ok)

        val restored = manager.beginRestore()
        assertTrue(restored.ok)
        assertTrue(File(workspace, "keep.py").isFile)

        manager.rollbackRestore(restored)

        assertFalse(File(workspace, "keep.py").exists())
        assertTrue(manager.hasRestorableClear())
    }
}
