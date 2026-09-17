import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice;
import java.lang.reflect.Method;
public class ACProbe5 {
    static void run(String clz, String code) throws Exception {
        Class<?> c = Class.forName(clz);
        Object p = c.getDeclaredConstructor().newInstance();
        RSyntaxDocument doc = new RSyntaxDocument(SyntaxConstants.SYNTAX_STYLE_PYTHON);
        doc.insertString(0, code, null);
        Method m = c.getMethod("parse", RSyntaxDocument.class, String.class);
        ParseResult r = (ParseResult) m.invoke(p, doc, SyntaxConstants.SYNTAX_STYLE_PYTHON);
        System.out.println(clz + " docLen=" + doc.getLength() + " notices=" + r.getNotices().size());
        for (ParserNotice n : r.getNotices())
            System.out.println("  line=" + n.getLine() + " off=" + n.getOffset() + " len=" + n.getLength() + " lvl=" + n.getLevel() + " msg=" + n.getMessage());
    }
    public static void main(String[] a) throws Exception {
        run("PycodestyleParser", "print");
        run("PythonCompileParser", "print");
        System.out.println("DONE");
    }
}
