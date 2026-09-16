import org.fife.ui.rsyntaxtextarea.*;
import javax.swing.*;

public class NoParserProbe {
    public static void main(String[] a) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                Class<?> kit = Class.forName("EditorRstaKt");
                // Build manual TANPA parser: tiru newPythonEditor secukupnya
                RSyntaxTextArea area = new RSyntaxTextArea();
                area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
                area.setCodeFoldingEnabled(true);
                area.setAntiAliasingEnabled(true);
                java.lang.reflect.Method theme = kit.getMethod("applyGithubDarkTheme", RSyntaxTextArea.class);
                theme.invoke(null, area);
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                area.setMarkOccurrences(false);
                area.setText("def foo(");
                org.fife.ui.rtextarea.RTextScrollPane pane = new org.fife.ui.rtextarea.RTextScrollPane(area);
                JFrame f = new JFrame("noparser");
                f.add(pane);
                f.setSize(450, 300);
                f.setLocation(100, 100);
                f.setVisible(true);
                System.out.println("shown, parsers=" + area.getParserCount());
                Thread.sleep(4000);
                f.dispose();
            } catch (Throwable t) { t.printStackTrace(); }
        });
        System.out.println("DONE");
        System.exit(0);
    }
}
