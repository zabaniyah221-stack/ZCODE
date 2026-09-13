import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Hasil satu eksekusi skrip. */
data class RunResult(val exitCode: Int, val output: String)

/**
 * Runner subprocess JVM-murni (tanpa sisa Android).
 * Menjalankan `python3 zcode_run.py <skrip>`; stdout+stderr digabung
 * berurutan (pemisahan stream oleh panel UI di Fase 1; demux menyusul).
 * Satu operasi = satu proses; tidak ada proses yatim (destroy di finally).
 */
object Runner {
    suspend fun run(script: File, runnerDir: File): RunResult = withContext(Dispatchers.IO) {
        val proc = ProcessBuilder(
            "python3",
            File(runnerDir, "zcode_run.py").absolutePath,
            script.absolutePath
        )
            .directory(script.parentFile)
            .redirectErrorStream(true)
            .start()
        try {
            val out = proc.inputStream.bufferedReader().readText()
            val code = proc.waitFor()
            RunResult(code, out)
        } finally {
            proc.destroy()
        }
    }

    /** Lokasi python3 sistem untuk status bar (path + versi penuh). */
    fun pythonInfo(): String = try {
        val proc = ProcessBuilder("python3", "--version")
            .redirectErrorStream(true).start()
        val ver = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        val which = ProcessBuilder("which", "python3").start()
            .inputStream.bufferedReader().readText().trim()
        "$which $ver"
    } catch (_: Exception) {
        "python3 tidak ditemukan"
    }
}
