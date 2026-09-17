import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;
import javax.swing.*;
import java.lang.reflect.Method;

public class FullWireProbe {
    public static void main(String[] a) throws Exception {
        final String text = a.length > 0 ? a[0] : "def";
        SwingUtilities.invokeAndWait(() -> {
            try {
                Class<?> kit = Class.forName("EditorRstaKt");
                Method m = kit.getMethod("newPythonEditor", int.class, String.class, kotlin.jvm.functions.Function1.class);
                RTextScrollPane pane = (RTextScrollPane) m.invoke(null, 14, text, (kotlin.jvm.functions.Function1<RSyntaxTextArea, kotlin.Unit>) area -> kotlin.Unit.INSTANCE);
                JFrame f = new JFrame("fullwire");
                f.add(pane);
                f.setSize(450, 300);
                f.setLocation(100, 100);
                f.setVisible(true);
                System.out.println("shown text=" + text);
        Thread.sleep(15000);
                Thread.sleep(60000);
                f.dispose();
            } catch (Throwable t) { t.printStackTrace(); }
        });
        System.out.println("DONE");
        System.exit(0);
    }
}
