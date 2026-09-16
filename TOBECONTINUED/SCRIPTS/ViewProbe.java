import org.fife.ui.rsyntaxtextarea.*;
import javax.swing.*;
import javax.swing.text.*;

public class ViewProbe {
    static void dump(View v, String ind) {
        System.out.println(ind + v.getClass().getSimpleName() + " [" + v.getStartOffset() + "," + v.getEndOffset() + "]");
        for (int i = 0; i < v.getViewCount(); i++) dump(v.getView(i), ind + "  ");
    }
    public static void main(String[] a) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RSyntaxTextArea area = new RSyntaxTextArea();
            area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            JFrame f = new JFrame("vp");
            f.add(new JScrollPane(area));
            f.setSize(500, 300);
            f.setVisible(true);
            area.setText("def foo(");
            Element root = area.getDocument().getDefaultRootElement();
            System.out.println("docLen=" + area.getDocument().getLength() + " lines=" + root.getElementCount());
            for (int i = 0; i < root.getElementCount(); i++) {
                Element e = root.getElement(i);
                System.out.println("line" + i + " [" + e.getStartOffset() + "," + e.getEndOffset() + "]");
            }
            dump(area.getUI().getRootView(area), "");
            f.dispose();
        });
        System.out.println("DONE");
        System.exit(0);
    }
}
