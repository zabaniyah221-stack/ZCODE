import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rtextarea.RTextScrollPane;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.image.BufferedImage;
import java.awt.*;
import java.lang.reflect.Method;
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice;
import java.util.List;

public class PaintProbe {
    public static void main(String[] a) throws Exception {
        final String text = a.length > 0 ? a[0] : "def";
        SwingUtilities.invokeAndWait(() -> {
            try {
                Class<?> kit = Class.forName("EditorRstaKt");
                Method m = kit.getMethod("newPythonEditor", int.class, String.class, kotlin.jvm.functions.Function1.class);
                final RTextScrollPane[] pp = new RTextScrollPane[1];
                RTextScrollPane pane = (RTextScrollPane) m.invoke(null, 14, text, (kotlin.jvm.functions.Function1<RSyntaxTextArea, kotlin.Unit>) area -> kotlin.Unit.INSTANCE);
                pp[0] = pane;
                RSyntaxTextArea area = (RSyntaxTextArea) pane.getViewport().getView();
                JFrame f = new JFrame("paintprobe");
                f.add(pane);
                f.setSize(450, 300);
                f.setLocation(100, 100);
                f.setVisible(true);
                // picu parse: sisip spasi lalu hapus (perubahan dokumen)
                area.getDocument().insertString(area.getDocument().getLength(), " ", null);
                Thread.sleep(300);
                area.getDocument().remove(area.getDocument().getLength() - 1, 1);
                Thread.sleep(2000);
                System.out.println("parserCount=" + area.getParserCount());
                try {
                    area.getClass().getMethod("forceReparsing", int.class).invoke(area, 0);
                    System.out.println("forced");
                } catch (Exception ex) { System.out.println("noforce " + ex.getMessage()); }
                Thread.sleep(5000);
                List<ParserNotice> notices = area.getParserNotices();
                System.out.println("notices=" + notices.size());
                for (ParserNotice n : notices)
                    System.out.println("  line=" + n.getLine() + " off=" + n.getOffset() + " len=" + n.getLength() + " lvl=" + n.getLevel());
                // tiru jalur paint RSTA per baris ke buffer
                int W = area.getWidth(), H = area.getHeight();
                System.out.println("area=" + W + "x" + H);
                BufferedImage img = new BufferedImage(Math.max(1, W), Math.max(1, H), BufferedImage.TYPE_INT_RGB);
                Graphics g = img.getGraphics();
                g.setColor(area.getBackground());
                g.fillRect(0, 0, W, H);
                RSyntaxTextAreaHighlighter h = (RSyntaxTextAreaHighlighter) area.getHighlighter();
                Element root = area.getDocument().getDefaultRootElement();
                View rootView = area.getUI().getRootView(area);
                for (int i = 0; i < root.getElementCount(); i++) {
                    Element e = root.getElement(i);
                    int ls = e.getStartOffset(), le = e.getEndOffset();
                    try {
                        Shape s = rootView.modelToView(ls, Position.Bias.Forward, le, Position.Bias.Backward, new Rectangle(0, 0, W, H));
                        Rectangle r = s.getBounds();
                        h.paintLayeredHighlights(g, ls, le, r, area, rootView);
                    } catch (Exception ex) { System.out.println("paint-err " + ex); }
                }
                g.dispose();
                // scan baris kuning/merah (squiggle): R tinggi, B rendah
                int[] rows = new int[H];
                for (int y = 0; y < H; y++) {
                    int c = 0;
                    for (int x = 0; x < W; x += 2) {
                        int rgb = img.getRGB(x, y);
                        int rr = (rgb >> 16) & 255, gg = (rgb >> 8) & 255, bb = rgb & 255;
                        if (rr > 150 && gg < 200 && bb < 120) c++;
                    }
                    rows[y] = c;
                }
                for (int y = 0; y < H; y++)
                    if (rows[y] > 5) System.out.println("PAINT-Y=" + y + " count=" + rows[y]);
                // ekspektasi: Y garis bawah baris 1
                try {
                    Rectangle lr = area.modelToView(0).getBounds();
                    System.out.println("line0rect=" + lr);
                } catch (Exception ex) { System.out.println("m2v-err " + ex); }
                f.dispose();
            } catch (Throwable t) { t.printStackTrace(); }
        });
        System.out.println("DONE");
        System.exit(0);
    }
}
