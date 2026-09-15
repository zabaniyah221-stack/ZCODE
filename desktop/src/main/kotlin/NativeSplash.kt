import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.PI
import kotlin.system.exitProcess

/**
 * Splash launcher native (15 Sep): window AWT fullscreen yang tampil di
 * detik pertama main() — jauh sebelum window Compose map (~13 detik di
 * Celeron). Di belakangnya ZCODE spawning normal; splash menutupinya.
 *
 * Alur: PRESS ENTER TO START → (Enter) roda gigi sampai app ready
 * (min 1 detik, tak pernah reveal setengah jadi) → fade 250ms → tutup +
 * fokus diserahkan ke editor. Anti-stuck: Esc tutup kapan saja;
 * auto-dismiss 3 detik setelah ready bila Enter tak ditekan.
 */
object SplashGate {
    val appReady = AtomicBoolean(false)

    /** Diisi pabrik editor sekali; dipanggil saat splash ditutup. */
    var focusEditor: (() -> Unit)? = null
}

private val SPLASH_BG = Color(0x0D, 0x11, 0x17)
private val SPLASH_ACCENT = Color(0x58, 0xA6, 0xFF)
private val SPLASH_TEXT = Color(0xC9, 0xD1, 0xD9)
private val SPLASH_DIM = Color(0x8B, 0x94, 0x9E)

/** Roda gigi digambar prosedural — tanpa asset, tanpa dependensi. */
private class GearView : JComponent() {
    var angle = 0.0

    init {
        preferredSize = Dimension(76, 76)
        maximumSize = Dimension(76, 76)
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.translate(width / 2.0, height / 2.0)
        g2.rotate(angle)
        g2.color = SPLASH_ACCENT
        for (i in 0 until 8) {
            g2.fillRect(-4, -30, 8, 12)
            g2.rotate(PI / 4)
        }
        g2.fillOval(-17, -17, 34, 34)
        g2.color = SPLASH_BG
        g2.fillOval(-8, -8, 16, 16)
    }
}

private fun splashLogoLabel(): JComponent {
    return try {
        val bytes = object {}::class.java
            .getResourceAsStream("/zcode_logo.png")?.readBytes()
            ?: return fallbackLogoLabel()
        val img = javax.imageio.ImageIO.read(bytes.inputStream())
        val w = 168
        val h = (w * img.height / img.width).coerceAtMost(168)
        val scaled = img.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH)
        JLabel(ImageIcon(scaled)).apply { alignmentX = JComponent.CENTER_ALIGNMENT }
    } catch (_: Exception) {
        fallbackLogoLabel()
    }
}

private fun fallbackLogoLabel(): JComponent =
    JLabel("{Z}").apply {
        foreground = SPLASH_ACCENT
        font = Font(Font.SANS_SERIF, Font.BOLD, 72)
        alignmentX = JComponent.CENTER_ALIGNMENT
    }

private fun statusLabel(text: String, size: Int, color: Color): JLabel =
    JLabel(text).apply {
        foreground = color
        font = Font(Font.SANS_SERIF, Font.PLAIN, size)
        alignmentX = JComponent.CENTER_ALIGNMENT
    }

/**
 * Tampilkan splash di EDT, kembali segera. Seluruh lifecycle (Enter, gear,
 * fade, auto-dismiss) diatur timer internal — pemanggil cukup set
 * [SplashGate.appReady] dan [SplashGate.focusEditor].
 */
fun showNativeSplash(): JFrame {
    val holder = arrayOfNulls<JFrame>(1)
    SwingUtilities.invokeAndWait {
        val frame = JFrame()
        frame.isUndecorated = true
        frame.background = SPLASH_BG
        frame.isAlwaysOnTop = true
        frame.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE

        val root = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = SPLASH_BG
        }
        root.add(Box.createVerticalGlue())
        root.add(splashLogoLabel())
        root.add(Box.createVerticalStrut(18))

        val gear = GearView().apply { isVisible = false }
        val prompt = statusLabel("PRESS ENTER TO START", 15, SPLASH_TEXT)
        val hint = statusLabel("Esc untuk lewati", 11, SPLASH_DIM)
        val load = statusLabel("memuat editor…", 12, SPLASH_DIM).apply { isVisible = false }
        root.add(gear)
        root.add(prompt)
        root.add(Box.createVerticalStrut(6))
        root.add(load)
        root.add(Box.createVerticalStrut(10))
        root.add(hint)
        root.add(Box.createVerticalGlue())
        frame.contentPane = root

        var phase = 0 // 0 tunggu, 1 gear, 2 selesai
        var readyAt = 0L
        var gearAt = 0L

        fun closeSplash() {
            if (phase == 2) return
            phase = 2
            try {
                val dev = frame.graphicsConfiguration?.device
                val canFade = dev?.isWindowTranslucencySupported(
                    java.awt.GraphicsDevice.WindowTranslucency.TRANSLUCENT) == true
                if (canFade) {
                    var op = 1.0f
                    Timer(25) { ev ->
                        op -= 0.1f
                        if (op <= 0f) {
                            (ev.source as Timer).stop()
                            finishSplash(frame)
                        } else try {
                            frame.opacity = op
                        } catch (_: Exception) {
                            (ev.source as Timer).stop()
                            finishSplash(frame)
                        }
                    }.start()
                } else {
                    finishSplash(frame)
                }
            } catch (_: Exception) {
                finishSplash(frame)
            }
        }

        fun beginDismiss() {
            if (phase != 0) return
            phase = 1
            gearAt = System.currentTimeMillis()
            prompt.isVisible = false
            hint.isVisible = false
            load.isVisible = true
            gear.isVisible = true
            root.revalidate()
        }

        // Tick 33ms: animasi gear + auto-dismiss 3 detik pasca-ready.
        val tick = Timer(33) {
            if (SplashGate.appReady.get() && readyAt == 0L) {
                readyAt = System.currentTimeMillis()
            }
            if (phase == 0 && readyAt != 0L &&
                System.currentTimeMillis() - readyAt > 3000) {
                beginDismiss()
            }
            if (phase == 1) {
                gear.angle += 0.18
                gear.repaint()
                if (SplashGate.appReady.get() &&
                    System.currentTimeMillis() - gearAt >= 1000) {
                    closeSplash()
                }
            }
        }
        tick.isRepeats = true
        tick.start()

        // Enter/Esc global level AWT — splash belum tentu pegang fokus.
        val mgr = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val keys = java.awt.KeyEventDispatcher { e ->
            if (e.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
            when (e.keyCode) {
                KeyEvent.VK_ENTER -> {
                    if (phase == 0) {
                        beginDismiss()
                        // Jangan bocor ke editor sebagai newline.
                        return@KeyEventDispatcher true
                    }
                    false
                }
                KeyEvent.VK_ESCAPE -> {
                    if (phase != 2) {
                        tick.stop()
                        finishSplash(frame)
                        return@KeyEventDispatcher true
                    }
                    false
                }
                else -> false
            }
        }
        mgr.addKeyEventDispatcher(keys)
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                exitProcess(0)
            }
        })
        // Simpan pelepas dispatcher untuk dipakai saat tutup.
        frame.rootPane.putClientProperty("splashKeys", keys)
        frame.rootPane.putClientProperty("splashTick", tick)

        // Windowed seukuran ZCODE (bukan fullscreen): menutup ZCODE saja,
        // bukan seluruh layar. Posisi tengah.
        // (fix 15 Sep: MAXIMIZED_BOTH salah konfigurasi.)
        frame.setSize(900, 600)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
        frame.toFront()
        holder[0] = frame
    }
    return holder[0]!!
}

private fun finishSplash(frame: JFrame) {
    try {
        val mgr = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
        (frame.rootPane.getClientProperty("splashKeys") as? java.awt.KeyEventDispatcher)
            ?.let { mgr.removeKeyEventDispatcher(it) }
        (frame.rootPane.getClientProperty("splashTick") as? Timer)?.stop()
    } catch (_: Exception) { }
    try {
        frame.isVisible = false
        frame.dispose()
    } catch (_: Exception) { }
    try {
        SplashGate.focusEditor?.invoke()
    } catch (_: Exception) { }
}
