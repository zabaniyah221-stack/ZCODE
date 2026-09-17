import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rsyntaxtextarea.parser.*;
import org.fife.ui.rtextarea.RTextScrollPane;
import javax.swing.*;
import java.lang.reflect.Method;
import java.util.List;

public class MgrProbe {
    public static class Dummy extends AbstractParser {
        public ParseResult parse(RSyntaxDocument doc, String style) {
            System.out.println("DUMMY-RUN docLen=" + doc.getLength());
            DefaultParseResult r = new DefaultParseResult(this);
            r.addNotice(new DefaultParserNotice(this, "dummy", 0, 0, 1));
            return r;
        }
    }
    public static void main(String[] a) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                Class<?> kit = Class.forName("EditorRstaKt");
                Method m = kit.getMethod("newPythonEditor", int.class, String.class, kotlin.jvm.functions.Function1.class);
                final RSyntaxTextArea[] box = new RSyntaxTextArea[1];
                RTextScrollPane pane = (RTextScrollPane) m.invoke(null, 14, "def", (kotlin.jvm.functions.Function1<RSyntaxTextArea, kotlin.Unit>) area -> { box[0] = area; return kotlin.Unit.INSTANCE; });
                RSyntaxTextArea area = box[0];
                area.addParser(new Dummy());
                JFrame f = new JFrame("mgrprobe");
                f.add(pane);
                f.setSize(450, 300);
                f.setVisible(true);
                area.getDocument().insertString(3, " ", null);
                Thread.sleep(6000);
                List<ParserNotice> ns = area.getParserNotices();
                System.out.println("notices=" + ns.size());
                for (ParserNotice n : ns) System.out.println("  " + n.getMessage() + " off=" + n.getOffset());
                f.dispose();
            } catch (Throwable t) { t.printStackTrace(); }
        });
        System.out.println("DONE");
        System.exit(0);
    }
}
