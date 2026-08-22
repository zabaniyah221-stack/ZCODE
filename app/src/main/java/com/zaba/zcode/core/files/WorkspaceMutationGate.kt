package com.zaba.zcode.core.files

/**
 * One monitor for workspace topology changes and their actual disk writes.
 *
 * A stale-save check is useful only when the write is performed before this
 * monitor is released. Otherwise Clear/Delete/Rename can commit between the
 * check and write and the old save can resurrect a file.
 */
internal class WorkspaceMutationGate {
    private val monitor = Any()

    fun <T> mutate(block: () -> T): T = synchronized(monitor) { block() }

    fun writeIfCurrent(
        isCurrent: () -> Boolean,
        write: () -> Unit,
    ): Boolean = synchronized(monitor) {
        if (!isCurrent()) return@synchronized false
        write()
        true
    }
}
