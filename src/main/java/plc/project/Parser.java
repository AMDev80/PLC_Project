package plc.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.math.BigInteger;
import java.math.BigDecimal;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling those functions.
 */
public final class Parser {
    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        List<Ast.Field> fields = new ArrayList<>();
        List<Ast.Method> methods = new ArrayList<>();

        while (tokens.has(0)) {
            if (peek("LET")) {
                fields.add(parseField());
            }
            else if (peek("DEF")) {
                methods.add(parseMethod());
            }
            else {
                throw new ParseException("Expected 'LET' or 'DEF' at the beginning of a field or method.", tokens.get(0).getIndex());
            }
        }
        return new Ast.Source(fields, methods);
    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a field, aka {@code LET}.
     */
    public Ast.Field parseField() throws ParseException { //updated after P4
        expect_token("LET");

        boolean constant = false;
        if (match("CONST")) {
            constant = true;
        }

        Token identifierToken = expect_token(Token.Type.IDENTIFIER);
        String name = identifierToken.getLiteral();

        // expect :
        expect_token(":");
        Token typeToken = expect_token(Token.Type.IDENTIFIER);
        String typeName = typeToken.getLiteral();

        Optional<Ast.Expression> value = Optional.empty();
        if (match("=")) {
            value = Optional.of(parseExpression());
        }

        expect_token(";");

        return new Ast.Field(name, typeName, constant, value);
    }

    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */
    public Ast.Method parseMethod() throws ParseException { //updated after P4
        expect_token("DEF");
        Token identifierToken = expect_token(Token.Type.IDENTIFIER);
        String name = identifierToken.getLiteral();

        expect_token("(");
        List<String> parameters = new ArrayList<>();
        List<String> parameterTypeNames = new ArrayList<>();

        if (!peek(")")) {
            do {
                Token paramToken = expect_token(Token.Type.IDENTIFIER);
                String paramName = paramToken.getLiteral();

                expect_token(":");
                Token typeToken = expect_token(Token.Type.IDENTIFIER);
                String paramTypeName = typeToken.getLiteral();

                parameters.add(paramName);
                parameterTypeNames.add(paramTypeName);
            }
            while (match(","));
        }

        expect_token(")");
        Optional<String> returnTypeName = Optional.empty();
        if (match(":")) {
            Token returnTypeToken = expect_token(Token.Type.IDENTIFIER);
            returnTypeName = Optional.of(returnTypeToken.getLiteral());
        }

        expect_token("DO");
        List<Ast.Statement> statements = new ArrayList<>();
        while (!peek("END")) {
            statements.add(parseStatement());
        }
        expect_token("END");

        return new Ast.Method(name, parameters, parameterTypeNames, returnTypeName, statements);
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, for, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Statement parseStatement() throws ParseException {
        if (peek("LET")) {
            return parseDeclarationStatement();
        }
        else if (peek("IF")) {
            return parseIfStatement();
        }
        else if (peek("FOR")) {
            return parseForStatement();
        }
        else if (peek("WHILE")) {
            return parseWhileStatement();
        }
        else if (peek("RETURN")) {
            return parseReturnStatement();
        }
        else {
            Ast.Expression expression = parseExpression();
            if (match("=")) {
                if (!(expression instanceof Ast.Expression.Access)) {
                    throw new ParseException("Invalid assignment target.", tokens.get(-1).getIndex());
                }
                Ast.Expression value = parseExpression();
                expect_token(";");
                return new Ast.Statement.Assignment(expression, value);
            }
            else {
                expect_token(";");
                return new Ast.Statement.Expression(expression);
            }
        }
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Statement.Declaration parseDeclarationStatement() throws ParseException {
        expect_token("LET");
        Token identifierToken = expect_token(Token.Type.IDENTIFIER);
        String name = identifierToken.getLiteral();

        Optional<String> typeName = Optional.empty();
        if (match(":")) {
            Token typeToken = expect_token(Token.Type.IDENTIFIER);
            typeName = Optional.of(typeToken.getLiteral());
        }

        Optional<Ast.Expression> value = Optional.empty();
        if (match("=")) {
            value = Optional.of(parseExpression());
        }

        expect_token(";");

        return new Ast.Statement.Declaration(name, typeName, value);
    }


    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Statement.If parseIfStatement() throws ParseException {
        expect_token("IF");
        Ast.Expression condition = parseExpression();
        expect_token("DO");

        List<Ast.Statement> then_statements = new ArrayList<>();
        while (!peek("ELSE") && !peek("END")) {
            then_statements.add(parseStatement());
        }

        List<Ast.Statement> else_statements = new ArrayList<>();
        if (match("ELSE")) {
            while (!peek("END")) {
                else_statements.add(parseStatement());
            }
        }

        expect_token("END");

        return new Ast.Statement.If(condition, then_statements, else_statements);
    }

    /**
     * Parses a for statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a for statement, aka
     * {@code FOR}.
     */
    public Ast.Statement.For parseForStatement() throws ParseException {
        expect_token("FOR");
        expect_token("(");

        Optional<Ast.Statement.Assignment> initialization = Optional.empty();
        if (!peek(";")) {
            Token identifierToken = expect_token(Token.Type.IDENTIFIER);
            String name = identifierToken.getLiteral();
            expect_token("=");
            Ast.Expression value = parseExpression();
            Ast.Expression.Access receiver = new Ast.Expression.Access(Optional.empty(), name);
            initialization = Optional.of(new Ast.Statement.Assignment(receiver, value));
        }
        expect_token(";");

        Ast.Expression condition = parseExpression();
        expect_token(";");

        Optional<Ast.Statement.Assignment> increment = Optional.empty();
        if (!peek(")")) {
            Token identifier_token = expect_token(Token.Type.IDENTIFIER);
            String name = identifier_token.getLiteral();
            expect_token("=");
            Ast.Expression value = parseExpression();
            Ast.Expression.Access receiver = new Ast.Expression.Access(Optional.empty(), name);
            increment = Optional.of(new Ast.Statement.Assignment(receiver, value));
        }
        expect_token(")");

        List<Ast.Statement> statements = new ArrayList<>();
        while (!peek("END")) {
            statements.add(parseStatement());
        }
        expect_token("END");

        return new Ast.Statement.For(
                initialization.orElse(null),
                condition,
                increment.orElse(null),
                statements
        );
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Statement.While parseWhileStatement() throws ParseException {
        expect_token("WHILE");
        Ast.Expression condition = parseExpression();
        expect_token("DO");

        List<Ast.Statement> statements = new ArrayList<>();
        while (!peek("END")) {
            statements.add(parseStatement());
        }

        expect_token("END");

        return new Ast.Statement.While(condition, statements);
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Statement.Return parseReturnStatement() throws ParseException {
        expect_token("RETURN");
        Ast.Expression value = parseExpression();
        expect_token(";");

        return new Ast.Statement.Return(value);
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expression parseExpression() throws ParseException {
        return parseLogicalExpression();
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    public Ast.Expression parseLogicalExpression() throws ParseException {
        Ast.Expression left = parseEqualityExpression();
        while (match("&&") || match("||")) {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expression right = parseEqualityExpression();
            left = new Ast.Expression.Binary(operator, left, right);
        }
        return left;
    }

    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expression parseEqualityExpression() throws ParseException {
        Ast.Expression left = parseAdditiveExpression();
        while (match("<") || match("<=") || match(">") || match(">=") || match("==") || match("!=")) {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expression right = parseAdditiveExpression();
            left = new Ast.Expression.Binary(operator, left, right);
        }
        return left;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expression parseAdditiveExpression() throws ParseException {
        Ast.Expression left = parseMultiplicativeExpression();
        while (match("+") || match("-")) {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expression right = parseMultiplicativeExpression();
            left = new Ast.Expression.Binary(operator, left, right);
        }
        return left;
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expression parseMultiplicativeExpression() throws ParseException {
        Ast.Expression left = parseSecondaryExpression();
        while (match("*") || match("/")) {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expression right = parseSecondaryExpression();
            left = new Ast.Expression.Binary(operator, left, right);
        }
        return left;
    }

    /**
     * Parses the {@code secondary-expression} rule.
     */
    public Ast.Expression parseSecondaryExpression() throws ParseException {
        Ast.Expression receiver = parsePrimaryExpression();
        while (match(".")) {
            Token identifier_token = expect_token(Token.Type.IDENTIFIER);
            String name = identifier_token.getLiteral();

            if (peek("(")) {
                match("(");
                List<Ast.Expression> arguments = new ArrayList<>();
                if (!peek(")")) {
                    do {
                        arguments.add(parseExpression());
                    }
                    while (match(","));
                }
                expect_token(")");
                receiver = new Ast.Expression.Function(Optional.of(receiver), name, arguments);
            }
            else {
                receiver = new Ast.Expression.Access(Optional.of(receiver), name);
            }
        }
        return receiver;
    }

    // helper expect_token function

    private Token expect_token(Object expected) throws ParseException {
        if (match(expected)) {
            return tokens.get(-1); // last matched token
        }
        else {
            if (tokens.has(0)) {
                throw new ParseException("Expected '" + expected + "' but found '" + tokens.get(0).getLiteral() + "'", tokens.get(0).getIndex());
            }
            else {
                throw new ParseException("Expected '" + expected + "' but found end of input", -1);
            }
        }
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expression parsePrimaryExpression() throws ParseException {
        if (match("NIL")) {
            return new Ast.Expression.Literal(null);
        }
        else if (match("TRUE")) {
            return new Ast.Expression.Literal(Boolean.TRUE);
        }
        else if (match("FALSE")) {
            return new Ast.Expression.Literal(Boolean.FALSE);
        }
        else if (match(Token.Type.INTEGER)) {
            String literal = tokens.get(-1).getLiteral();
            BigInteger value = new BigInteger(literal);
            return new Ast.Expression.Literal(value);
        }
        else if (match(Token.Type.DECIMAL)) {
            String literal = tokens.get(-1).getLiteral();
            BigDecimal value = new BigDecimal(literal);
            return new Ast.Expression.Literal(value);
        }
        else if (match(Token.Type.CHARACTER)) {
            String literal = tokens.get(-1).getLiteral();
            // removing surrounding single quotes within the token literal
            literal = literal.substring(1, literal.length() - 1);
            // escape sequences handler
            literal = literal.replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\b", "\b")
                    .replace("\\r", "\r")
                    .replace("\\'", "'")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
            if (literal.length() != 1) {
                throw new ParseException("Invalid character literal", tokens.get(-1).getIndex());
            }
            return new Ast.Expression.Literal(literal.charAt(0));
        }
        else if (match(Token.Type.STRING)) {
            String literal = tokens.get(-1).getLiteral();
            // removing surrounding double quotes within the token literal
            literal = literal.substring(1, literal.length() - 1);
            // esc seq handler
            literal = literal.replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\b", "\b")
                    .replace("\\r", "\r")
                    .replace("\\'", "'")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
            return new Ast.Expression.Literal(literal);
        }
        else if (match("(")) {
            Ast.Expression expression = parseExpression();
            expect_token(")");
            return new Ast.Expression.Group(expression);
        }
        else if (match(Token.Type.IDENTIFIER)) {
            String name = tokens.get(-1).getLiteral();
            if (match("(")) {
                List<Ast.Expression> arguments = new ArrayList<>();
                if (!peek(")")) {
                    do {
                        arguments.add(parseExpression());
                    } while (match(","));
                }
                expect_token(")");
                return new Ast.Expression.Function(Optional.empty(), name, arguments);
            }
            else {
                return new Ast.Expression.Access(Optional.empty(), name);
            }
        }
        else {
            throw new ParseException("Expected an expression", tokens.has(0) ? tokens.get(0).getIndex() : -1);
        }
    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     *
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private boolean peek(Object... patterns) { // from class
        for (int i = 0; i < patterns.length; i++) {
            if (!tokens.has(i)) {
                return false;
            }
            else if (patterns[i] instanceof Token.Type) {
                if (patterns[i] != tokens.get(i).getType()) {
                    return false;
                }
            }
            else if (patterns[i] instanceof String) {
                if (!patterns[i].equals(tokens.get(i).getLiteral())) {
                    return false;
                }
            }
            else {
                throw new AssertionError("Invalid pattern object: " + patterns[i].getClass());
            }
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) { // from class
        boolean peek = peek(patterns);
        if (peek) {
            for (int i = 0; i < patterns.length; i++) {
                tokens.advance();
            }
        }
        return peek;
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}
