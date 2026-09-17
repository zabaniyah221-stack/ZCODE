import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
public class MarkProbe {
    public static void main(String[] a) {
        RSyntaxTextArea t = new RSyntaxTextArea();
        System.out.println("mark=" + t.getMarkOccurrencesColor());
        System.out.println("sel=" + t.getSelectionColor());
    }
}
