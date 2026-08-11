package com.zaba.zcode.core.execution

/**
 * SessionState — lifecycle eksplisit interactive session (SPEC-001 §17).
 *
 * START → RUNNING ⇄ WAITING_FOR_INPUT → INTERRUPTING/STOPPING → EXITED/FAILED
 *
 * TIDAK ada hard timeout: session selesai hanya karena process exit, Ctrl+C,
 * Stop/Back dari user, exception, atau kegagalan OS/process.
 */
enum class SessionState {
    START,
    RUNNING,
    WAITING_FOR_INPUT,
    INTERRUPTING,
    STOPPING,
    EXITED,
    FAILED;

    fun isTerminal(): Boolean = this == EXITED || this == FAILED
}
