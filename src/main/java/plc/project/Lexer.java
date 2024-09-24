package plc.project;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import static plc.project.Token.Type.*;

/**
 * The lexer works through three main functions:
 *
 *  - {@link #lex()}, which repeatedly calls lexToken() and skips whitespace
 *  - {@link #lexToken()}, which lexes the next token
 *  - {@link CharStream}, which manages the state of the lexer and literals
 *
 * If the lexer fails to parse something (such as an unterminated string) you
 * should throw a {@link ParseException} with an index at the invalid character.
 *
 * The {@link #peek(String...)} and {@link #match(String...)} functions are
 * helpers you need to use, they will make the implementation easier.
 */
public final class Lexer {

    private final CharStream chars;

//    public static final Pattern
//        ID_PAT = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*"),
//        INT_PAT = Pattern.compile("0|[+-]?[1-9][0-9]*"),
//        DEC_PAT = Pattern.compile("[+-]?(0|[1-9][0-9]*)\\.[0-9]+"),
//        CHAR_PAT = Pattern.compile("'([^'\\\\]|\\\\[brnt'\"\\\\])'"),
//        STRING_PAT = Pattern.compile("\"([^\"\\n\\r\\\\]|\\\\[brnt'\"\\\\])*\""),
//        ESC_PAT = Pattern.compile("\\\\[brnt'\"\\\\]"),
//        OP_PAT = Pattern.compile("[<>!=]=?|&&|\\|\\||."),
//        WP_PAT = Pattern.compile("[ \\x08\\n\\r\\t]");// topdown

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    /**
     * Repeatedly lexes the input using {@link #lexToken()}, also skipping over
     * whitespace where appropriate.
     */
    public List<Token> lex() {
        List<Token> lo_tokens = new LinkedList<>();
        while (chars.has(0)) {
            if (peek("[ \\x08\\n\\r\\t]+")) {
                while (match("[ \\x08\\n\\r\\t]+")) {
                    // Skipping whitespace
                }
                chars.skip();
            }
            else {
                lo_tokens.add(lexToken());
            }
        }
        return lo_tokens;
    }

    /**
     * This method determines the type of the next token, delegating to the
     * appropriate lex method. As such, it is best for this method to not change
     * the state of the char stream (thus, use peek not match).
     *
     * The next character should start a valid token since whitespace is handled
     * by {@link #lex()}
     */
    public Token lexToken() {
        if (peek("[A-Za-z_]")) {
            return lexIdentifier();
        }
        else if (peek("[+-]") || peek("[1-9]") || peek("0")) {
            return lexNumber();
        }
        else if (peek("'")) {
            return lexCharacter();
        }
        else if (peek("\"")) {
            return lexString();
        }
        else if (peek("[^\\s]")) {
            return lexOperator();
        }
        throw new ParseException("Invalid token within: " + chars.input, chars.index);
    }

    public Token lexIdentifier() {
        chars.advance(); // matched first char with peek from lexToken()
        while (peek("[A-Za-z0-9_-]")) {
            chars.advance();
        }
        return chars.emit(IDENTIFIER);
    }

    public Token lexNumber() { // both decimal or integer
        if (match("[+-]")) {
            // Optional sign for either decimal or integer
        }
        boolean is_decimal = false;
        if (match("0")) {
            // leading zero
        }
        else if (match("[1-9]")) {
            while (match("[0-9]")) {
                // go through digits
            }
        }
        else {
            throw new ParseException("Invalid number", chars.index);
        }

        if (match("\\.")) {
            is_decimal = true;
            if (!match("[0-9]")) {
                throw new ParseException("Expected digits after . in decimal", chars.index);
            }
            while (match("[0-9]")) {
                // go through decimal digits
            }
        }
        return chars.emit(is_decimal ? Token.Type.DECIMAL : Token.Type.INTEGER);
    }

    public Token lexCharacter() {
        if (!match("'")) {
            throw new ParseException("Character does not start with a single quote", chars.index);
        }

        if (match("[^'\\\\\\n\\r]")) {
            // any following valid chars
        }
        else if (match("\\\\")) {
            if (!match("[bnrt'\"\\\\]")) {
                throw new ParseException("Invalid escape sequence", chars.index);
            }
        }
        else {
            throw new ParseException("Invalid character in character literal", chars.index);
        }

        if (!match("'")) {
            throw new ParseException("Character literal must end with a single quote", chars.index);
        }

        return chars.emit(CHARACTER);
    }

    public Token lexString() {
        if (!match("\"")) {
            throw new ParseException("String literal does not start with a double quote", chars.index);
        }

        while (!peek("\"")) {
            if (!chars.has(0) || peek("\\n", "\\r")) {
                throw new ParseException("Unterminated string literal", chars.index);
            }
            if (match("\\\\")) {
                if (!match("[bnrt'\"\\\\]")) {
                    throw new ParseException("Invalid escape sequence", chars.index);
                }
            }
            else {
                chars.advance();
            }
        }

        match("\""); // ending the string literal
        return chars.emit(STRING);
    }

    public void lexEscape() {
        chars.index++;
    }

    public Token lexOperator() {
        if (match("[<>!=]", "=")) {
            return chars.emit(OPERATOR);
        }
        else if (match("&", "&") || match("\\|", "\\|")) {
            return chars.emit(OPERATOR);
        }
        else if (match("[^\\s]")) {
            return chars.emit(OPERATOR);
        }
        else {
            throw new ParseException("Invalid operator", chars.index);
        }
    }

    /**
     * Returns true if the next sequence of characters match the given patterns,
     * which should be a regex. For example, {@code peek("a", "b", "c")} would
     * return true if the next characters are {@code 'a', 'b', 'c'}.
     */
    public boolean peek(String... patterns) { // accepts regex
        for (int i = 0; i < patterns.length; i++) {
            if (!chars.has(i) || !String.valueOf(chars.get(i)).matches(patterns[i]) ) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true in the same way as {@link #peek(String...)}, but also
     * advances the character stream past all matched characters if peek returns
     * true. Hint - it's easiest to have this method simply call peek.
     */
    public boolean match(String... patterns) { // will advance chars
        boolean peek = peek(patterns);
        if (peek) {
            for (int i = 0; i < patterns.length; i++) {
                chars.advance();
            }
        }
        return peek;
    }

    /**
     * A helper class maintaining the input string, current index of the char
     * stream, and the current length of the token being matched.
     *
     * You should rely on peek/match for state management in nearly all cases.
     * The only field you need to access is {@link #index} for any {@link
     * ParseException} which is thrown.
     */
    public static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        // starting at index + offset
        // has_left
        public boolean has(int offset) {
            return index + offset < input.length();
        }

        // get char_at (do if has then get)
        public char get(int offset) {
            return input.charAt(index + offset);
        }

        public void advance() {
            index++;
            length++;
        }

        public void skip() {
            length = 0;
        } // whitespace too

        public Token emit(Token.Type type) {
            int start = index - length;
            skip();  // length = 0
            return new Token(type, input.substring(start, index), start);
        }

    }

}


//0123456789
//LET x = 5;