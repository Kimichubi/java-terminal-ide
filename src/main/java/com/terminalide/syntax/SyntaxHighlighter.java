package com.terminalide.syntax;

import com.googlecode.lanterna.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxHighlighter {

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
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

    public enum TokenType {
        KEYWORD, STRING, CHAR, LINE_COMMENT, BLOCK_COMMENT, NUMBER, CONSTANT, ANNOTATION, DEFAULT
    }

    public record Token(int start, int end, TokenType type) {}

    public List<Token> tokenize(String line) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(line);
        while (matcher.find()) {
            TokenType type;
            String matched = matcher.group();
            if (matched.startsWith("//")) {
                type = TokenType.LINE_COMMENT;
            } else if (matched.startsWith("/*")) {
                type = TokenType.BLOCK_COMMENT;
            } else if (matched.startsWith("\"")) {
                type = TokenType.STRING;
            } else if (matched.startsWith("'")) {
                type = TokenType.CHAR;
            } else if (matched.startsWith("@")) {
                type = TokenType.ANNOTATION;
            } else if (Character.isDigit(matched.charAt(0))) {
                type = TokenType.NUMBER;
            } else if (matched.chars().allMatch(c -> Character.isUpperCase(c) || c == '_' || Character.isDigit(c))) {
                type = TokenType.CONSTANT;
            } else {
                type = TokenType.KEYWORD;
            }
            tokens.add(new Token(matcher.start(), matcher.end(), type));
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
            default -> TextColor.ANSI.DEFAULT;
        };
    }
}
