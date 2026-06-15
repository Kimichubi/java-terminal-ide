package com.terminalide.syntax;

import com.googlecode.lanterna.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxHighlighter {

    public enum TokenType {
        KEYWORD, STRING, CHAR, LINE_COMMENT, BLOCK_COMMENT, NUMBER, CONSTANT, ANNOTATION, TAG, ATTRIBUTE, PROPERTY
    }

    public record Token(int start, int end, TokenType type) {}

    public interface LanguageDef {
        Pattern pattern();
        TokenType resolveType(String match);
    }

    private LanguageDef currentLang;

    private static final LanguageDef JAVA = new LanguageDef() {
        private static final Pattern P = Pattern.compile(
            "\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|" +
            "continue|default|do|double|else|enum|extends|final|finally|float|for|" +
            "goto|if|implements|import|instanceof|int|interface|long|native|new|" +
            "package|private|protected|public|return|short|static|strictfp|super|" +
            "switch|synchronized|this|throw|throws|transient|try|void|volatile|while|" +
            "var|record|sealed|permits|yield|module|exports|opens|requires|to|" +
            "transitive|uses|provides|with)\\b" +
            "|\"(?:\\\\.|[^\"\\\\])*\"" +
            "|'[^'\\\\]'" +
            "|//.*" +
            "|/\\*[\\s\\S]*?\\*/" +
            "|\\b\\d+(\\.\\d+)?[fFlLdD]?\\b" +
            "|\\b[A-Z][A-Z0-9_]*\\b" +
            "|@\\w+"
        );
        public Pattern pattern() { return P; }
        public TokenType resolveType(String m) {
            if (m.startsWith("//")) return TokenType.LINE_COMMENT;
            if (m.startsWith("/*")) return TokenType.BLOCK_COMMENT;
            if (m.startsWith("\"")) return TokenType.STRING;
            if (m.startsWith("'")) return TokenType.CHAR;
            if (m.startsWith("@")) return TokenType.ANNOTATION;
            if (Character.isDigit(m.charAt(0))) return TokenType.NUMBER;
            if (m.chars().allMatch(c -> Character.isUpperCase(c) || c == '_' || Character.isDigit(c)))
                return TokenType.CONSTANT;
            return TokenType.KEYWORD;
        }
    };

    private static final LanguageDef PYTHON = new LanguageDef() {
        private static final Pattern P = Pattern.compile(
            "\\b(and|as|assert|async|await|break|class|continue|def|del|elif|else|except|" +
            "finally|for|from|global|if|import|in|is|lambda|nonlocal|not|or|pass|raise|" +
            "return|try|while|with|yield|True|False|None|self)\\b" +
            "|\"\"\"[\\s\\S]*?\"\"\"" +
            "|'''[\\s\\S]*?'''" +
            "|\"(?:\\\\.|[^\"\\\\])*\"" +
            "|'(?:\\\\.|[^'\\\\])*'" +
            "|#.*" +
            "|\\b\\d+(\\.\\d+)?(e\\d+)?\\b" +
            "|@\\w+"
        );
        public Pattern pattern() { return P; }
        public TokenType resolveType(String m) {
            if (m.startsWith("#")) return TokenType.LINE_COMMENT;
            if (m.startsWith("\"\"\"") || m.startsWith("'''")) return TokenType.BLOCK_COMMENT;
            if (m.startsWith("\"") || m.startsWith("'")) return TokenType.STRING;
            if (m.startsWith("@")) return TokenType.ANNOTATION;
            if (Character.isDigit(m.charAt(0))) return TokenType.NUMBER;
            return TokenType.KEYWORD;
        }
    };

    private static final LanguageDef JAVASCRIPT = new LanguageDef() {
        private static final Pattern P = Pattern.compile(
            "\\b(await|break|case|catch|class|const|continue|debugger|default|delete|do|" +
            "else|export|extends|finally|for|function|if|import|in|instanceof|let|new|" +
            "of|return|super|switch|this|throw|try|typeof|var|void|while|with|yield|" +
            "async|static|from|as|true|false|null|undefined|NaN|Infinity)\\b" +
            "|`[^`]*`" +
            "|\"(?:\\\\.|[^\"\\\\])*\"" +
            "|'(?:\\\\.|[^'\\\\])*'" +
            "|//.*" +
            "|/\\*[\\s\\S]*?\\*/" +
            "|\\b\\d+(\\.\\d+)?(e\\d+)?\\b"
        );
        public Pattern pattern() { return P; }
        public TokenType resolveType(String m) {
            if (m.startsWith("//")) return TokenType.LINE_COMMENT;
            if (m.startsWith("/*")) return TokenType.BLOCK_COMMENT;
            if (m.startsWith("\"") || m.startsWith("'") || m.startsWith("`")) return TokenType.STRING;
            if (Character.isDigit(m.charAt(0))) return TokenType.NUMBER;
            return TokenType.KEYWORD;
        }
    };

    private static final LanguageDef JSON = new LanguageDef() {
        private static final Pattern P = Pattern.compile(
            "\"(?:\\\\.|[^\"\\\\])*\"" +
            "|\\b-?\\d+(\\.\\d+)?(e[+\\-]?\\d+)?\\b" +
            "|\\b(true|false|null)\\b"
        );
        public Pattern pattern() { return P; }
        public TokenType resolveType(String m) {
            if (m.startsWith("\"")) return TokenType.STRING;
            if (m.startsWith("true") || m.startsWith("false") || m.startsWith("null")) return TokenType.KEYWORD;
            return TokenType.NUMBER;
        }
    };

    private static final LanguageDef XML = new LanguageDef() {
        private static final Pattern P = Pattern.compile(
            "</?[A-Za-z][\\w:.\\-]*" +
            "|/?>" +
            "|\\b[A-Za-z][\\w:.\\-]*\\s*=" +
            "|\"(?:\\\\.|[^\"\\\\])*\"" +
            "|'(?:\\\\.|[^'\\\\])*'" +
            "|<!--[\\s\\S]*?-->"
        );
        public Pattern pattern() { return P; }
        public TokenType resolveType(String m) {
            if (m.startsWith("<!--")) return TokenType.BLOCK_COMMENT;
            if (m.startsWith("\"") || m.startsWith("'")) return TokenType.STRING;
            if (m.endsWith("=")) return TokenType.ATTRIBUTE;
            return TokenType.TAG;
        }
    };

    private static final LanguageDef SQL = new LanguageDef() {
        private static final Pattern P = Pattern.compile(
            "\\b(SELECT|FROM|WHERE|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CREATE|TABLE|" +
            "ALTER|DROP|INDEX|VIEW|JOIN|LEFT|RIGHT|INNER|OUTER|ON|AND|OR|NOT|IN|LIKE|" +
            "BETWEEN|IS|NULL|AS|ORDER|BY|GROUP|HAVING|LIMIT|OFFSET|UNION|ALL|DISTINCT|" +
            "CASE|WHEN|THEN|ELSE|END|BEGIN|COMMIT|ROLLBACK|PRIMARY|KEY|FOREIGN|REFERENCES|" +
            "INT|INTEGER|VARCHAR|TEXT|BOOLEAN|DATE|TIMESTAMP|FLOAT|DOUBLE|DECIMAL|SERIAL|" +
            "BIGINT|SMALLINT|CHAR|CONSTRAINT|DEFAULT|UNIQUE|CHECK|EXISTS|ANY|SOME|" +
            "COUNT|SUM|AVG|MIN|MAX|TRUNCATE|GRANT|REVOKE|EXEC|EXECUTE|PROCEDURE|FUNCTION)\\b" +
            "|'[^']*'" +
            "|--.*" +
            "|/\\*[\\s\\S]*?\\*/" +
            "|\\b\\d+(\\.\\d+)?\\b"
        );
        public Pattern pattern() { return P; }
        public TokenType resolveType(String m) {
            if (m.startsWith("--")) return TokenType.LINE_COMMENT;
            if (m.startsWith("/*")) return TokenType.BLOCK_COMMENT;
            if (m.startsWith("'")) return TokenType.STRING;
            if (Character.isDigit(m.charAt(0))) return TokenType.NUMBER;
            return TokenType.KEYWORD;
        }
    };

    private static final LanguageDef YAML = new LanguageDef() {
        private static final Pattern P = Pattern.compile(
            "#.*" +
            "|\"[^\"]*\"" +
            "|'[^']*'" +
            "|\\b\\d+(\\.\\d+)?\\b" +
            "|\\b(true|false|null|yes|no|on|off)\\b" +
            "|^[A-Za-z][\\w\\-]*\\s*:"
        );
        public Pattern pattern() { return P; }
        public TokenType resolveType(String m) {
            if (m.startsWith("#")) return TokenType.LINE_COMMENT;
            if (m.startsWith("\"") || m.startsWith("'")) return TokenType.STRING;
            if (Character.isDigit(m.charAt(0))) return TokenType.NUMBER;
            if (m.endsWith(":")) return TokenType.PROPERTY;
            return TokenType.KEYWORD;
        }
    };

    private static final Map<Set<String>, LanguageDef> EXTENSIONS = Map.of(
        Set.of("java", "kt", "scala", "groovy"), JAVA,
        Set.of("py", "pyw", "pyi"), PYTHON,
        Set.of("js", "ts", "jsx", "tsx", "mjs", "cjs"), JAVASCRIPT,
        Set.of("json", "jsonc", "json5"), JSON,
        Set.of("xml", "html", "htm", "xhtml", "svg", "xsd", "xsl", "wsdl"), XML,
        Set.of("sql", "psql", "mysql", "ddl", "dml"), SQL,
        Set.of("yml", "yaml"), YAML
    );

    public SyntaxHighlighter() {
        this.currentLang = JAVA;
    }

    public void detectLanguage(String filePath) {
        if (filePath == null) { currentLang = JAVA; return; }
        String ext = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();
        for (var entry : EXTENSIONS.entrySet()) {
            if (entry.getKey().contains(ext)) {
                currentLang = entry.getValue();
                return;
            }
        }
        currentLang = JAVA;
    }

    public List<Token> tokenize(String line) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = currentLang.pattern().matcher(line);
        while (matcher.find()) {
            tokens.add(new Token(matcher.start(), matcher.end(), currentLang.resolveType(matcher.group())));
        }
        return tokens;
    }

    public TextColor colorFor(TokenType type) {
        return switch (type) {
            case KEYWORD -> TextColor.ANSI.BLUE;
            case STRING -> TextColor.ANSI.GREEN;
            case CHAR -> TextColor.ANSI.GREEN;
            case LINE_COMMENT, BLOCK_COMMENT -> TextColor.ANSI.BLACK_BRIGHT;
            case NUMBER -> TextColor.ANSI.CYAN;
            case CONSTANT -> TextColor.ANSI.YELLOW;
            case ANNOTATION -> TextColor.ANSI.MAGENTA;
            case TAG -> TextColor.ANSI.RED;
            case ATTRIBUTE -> TextColor.ANSI.YELLOW;
            case PROPERTY -> TextColor.ANSI.MAGENTA;
        };
    }
}
