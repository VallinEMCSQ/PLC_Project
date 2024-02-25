package plc.project;

import javax.swing.text.html.HTMLDocument;
import java.util.List;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

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
 * to calling that functions.
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
        List<Ast.Global> globalList = new ArrayList<Ast.Global>();
        List<Ast.Function> functionList = new ArrayList<Ast.Function>();
        while (peek("LIST") || peek("VAR") || peek("VAL")){
            globalList.add(parseGlobal());
        }
        while (peek("FUN")){
            functionList.add(parseFunction());
        }
        return new Ast.Source(globalList, functionList);
    }

    /**
     * Parses the {@code global} rule. This method should only be called if the
     * next tokens start a global, aka {@code LIST|VAL|VAR}.
     */
    public Ast.Global parseGlobal() throws ParseException {
        if(peek("LIST")){
            match("LIST");
            return parseList();
        } else if (peek("VAR")) {
            match("VAR");
            return parseMutable();
        } else if (peek("VAL")) {
            match("VAL");
            return parseImmutable();
        } else {
            throw new ParseException("Invalid Global", tokens.index);
        }
    }

    /**
     * Parses the {@code list} rule. This method should only be called if the
     * next token declares a list, aka {@code LIST}.
     */
    public Ast.Global parseList() throws ParseException {
        if(peek(Token.Type.IDENTIFIER)){
            String identifier = tokens.get(0).getLiteral();
            match(identifier);
            Optional<Ast.Expression> value;

            if(match("=")){
                if(match("[")){
                    value = Optional.of(parseExpression());
                    while(peek(",")){
                        value = Optional.of(parseExpression());
                    }
                    if(match("]")){
                        return new Ast.Global(identifier, true, value);
                    }
                }
            }
        }
        throw new ParseException("Invalid List", tokens.index);
    }

    /**
     * Parses the {@code mutable} rule. This method should only be called if the
     * next token declares a mutable global variable, aka {@code VAR}.
     */
    public Ast.Global parseMutable() throws ParseException {
        if (peek(Token.Type.IDENTIFIER)){
            String identifier = tokens.get(0).getLiteral();
            match(identifier);
            if(match("=")){
                return new Ast.Global(identifier, true, Optional.of(parseExpression()));
            }
            else{
                return new Ast.Global(identifier, true, Optional.empty());
            }
        }
        throw new ParseException("Invalid Mutable", tokens.index);
    }

    /**
     * Parses the {@code immutable} rule. This method should only be called if the
     * next token declares an immutable global variable, aka {@code VAL}.
     */
    public Ast.Global parseImmutable() throws ParseException {
        if(peek(Token.Type.IDENTIFIER)){
            String identifier = tokens.get(0).getLiteral();
            match(identifier);
            if(match("=")){
                return new Ast.Global(identifier, false, Optional.of(parseExpression()));
            }
        }
        throw new ParseException("Invalid Immutable", tokens.index);
    }

    /**
     * Parses the {@code function} rule. This method should only be called if the
     * next tokens start a method, aka {@code FUN}.
     */
    public Ast.Function parseFunction() throws ParseException {
        match("FUN");

        String identifier;
        List<String> parameters = new ArrayList<>();
        List<Ast.Statement> statements = new ArrayList<>();

        if (peek(Token.Type.IDENTIFIER)) {
            identifier = tokens.get(0).getLiteral();
            match(identifier);

            if (match("(")) {
                if (peek(Token.Type.IDENTIFIER)) {
                    do {
                        parameters.add(tokens.get(0).getLiteral());
                        match(Token.Type.IDENTIFIER);
                    } while (match(","));
                }

                if (!match(")")) {
                    throw new ParseException("Invalid Function", tokens.index);
                }

                match("DO");

                while (!peek("END")) {
                    statements.add(parseStatement());
                }

                if (match("END")) {
                    return new Ast.Function(identifier, parameters, statements);
                } else {
                    throw new ParseException("Expected END", tokens.index);
                }
            }
        }

        throw new ParseException("Invalid Function", tokens.index);
    }
    /**
     * Parses the {@code block} rule. This method should only be called if the
     * preceding token indicates the opening a block of statements.
     */
    public List<Ast.Statement> parseBlock() throws ParseException {
        throw new UnsupportedOperationException(); //TODO
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Statement parseStatement() throws ParseException {
        Ast.Statement statement;

        if (peek("LET")) {
            statement = parseDeclarationStatement();
        } else if (peek("RETURN")) {
            statement = parseReturnStatement();
        } else if (peek("SWITCH")) {
            statement = parseSwitchStatement();
        } else if (peek("WHILE")) {
            statement = parseWhileStatement();
        } else if (peek("IF")) {
            statement = parseIfStatement();
        } else {
            Ast.Expression left = parseExpression();

            if (match("=")) {
                Ast.Expression right = parseExpression();
                if (match(";")) {
                    statement = new Ast.Statement.Assignment(left, right);
                } else {
                    throw new ParseException("Expected semicolon", tokens.index);
                }
            } else if (match(";")) {
                statement = new Ast.Statement.Expression(left);
            } else {
                throw new ParseException("Expected semicolon", tokens.index);
            }
        }

        return statement;
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Statement.Declaration parseDeclarationStatement() throws ParseException {
        match("LET");

        if (!peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier after LET", tokens.index);
        }

        String name = tokens.get(0).getLiteral();
        match(Token.Type.IDENTIFIER);

        Optional<Ast.Expression> value = Optional.empty();

        if (match("=")) {
            value = Optional.of(parseExpression());
        }

        if (!match(";")) {
            throw new ParseException("Expected semicolon", tokens.index);
        }

        return new Ast.Statement.Declaration(name, value);
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Statement.If parseIfStatement() throws ParseException {
        match("IF");

        Ast.Expression condition = parseExpression();
        List<Ast.Statement> thenStatements = new ArrayList<Ast.Statement>();
        List<Ast.Statement> elseStatements = new ArrayList<Ast.Statement>();

        if (match("DO")) {
            while (!peek("ELSE") && !peek("END")) {
                thenStatements.add(parseStatement());
            }

            if (match("ELSE")) {
                while (!peek("END")) {
                    elseStatements.add(parseStatement());
                }
            }

            if (match("END")) {
                return new Ast.Statement.If(condition, thenStatements, elseStatements);
            } else {
                throw new ParseException("Expected END", tokens.index);
            }
        } else {
            throw new ParseException("Expected DO", tokens.index);
        }
    }

    /**
     * Parses a switch statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a switch statement, aka
     * {@code SWITCH}.
     */
    public Ast.Statement.Switch parseSwitchStatement() throws ParseException {
        if (match("SWITCH")){
            Ast.Expression condition = parseExpression();
            List<Ast.Statement.Case> cases = new ArrayList<Ast.Statement.Case>();
            while (peek("CASE") || peek("DEFAULT")){
                cases.add(parseCaseStatement());
            }
            return new Ast.Statement.Switch(condition, cases);
        }
        throw new ParseException("Invalid Switch Statement", tokens.index);
    }

    /**
     * Parses a case or default statement block from the {@code switch} rule. 
     * This method should only be called if the next tokens start the case or 
     * default block of a switch statement, aka {@code CASE} or {@code DEFAULT}.
     */
    public Ast.Statement.Case parseCaseStatement() throws ParseException {
        Ast.Statement.Case caseStatement = null;
        if (peek("CASE")){
            Ast.Expression value = parseExpression();
            if (match(":")){
                List<Ast.Statement> statements = new ArrayList<Ast.Statement>();
                while (!peek("CASE") && !peek("DEFAULT")){
                    statements.add(parseStatement());
                }
                caseStatement = new Ast.Statement.Case(Optional.of(value), statements);
            }
        } else if (match("DEFAULT")) {
            List<Ast.Statement> statements = new ArrayList<Ast.Statement>();
            while (!match("END")){
                statements.add(parseStatement());
            }
            caseStatement = new Ast.Statement.Case(Optional.empty(), statements);
        } else {
            throw new ParseException("Invalid Case Statement", tokens.index);
        }
        return caseStatement;
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Statement.While parseWhileStatement() throws ParseException {
        if(match("WHILE")){
            Ast.Expression condition = parseExpression();
            if(match("DO")){
                List<Ast.Statement> statements = new ArrayList<Ast.Statement>();
                while (!match("END")){
                    statements.add(parseStatement());
                }
                return new Ast.Statement.While(condition, statements);
            }
        }
        throw new ParseException("Invalid While Statement", tokens.index);
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Statement.Return parseReturnStatement() throws ParseException {
        if(match("RETURN")){
            Ast.Expression value = parseExpression();
            if (match(";")){
                return new Ast.Statement.Return(value);
            }
        }
        throw new ParseException("Invalid Return", tokens.index);

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
        Ast.Expression leftOperand =  parseComparisonExpression();
        if (peek("||") || peek("&&")){
            String operator = tokens.get(0).getLiteral();
            match(operator);
            Ast.Expression rightOperand =  parseComparisonExpression();
            leftOperand = new Ast.Expression.Binary(operator, leftOperand, rightOperand);
        }
        return leftOperand;
    }

    /**
     * Parses the {@code comparison-expression} rule.
     */
    public Ast.Expression parseComparisonExpression() throws ParseException {
        Ast.Expression leftOperand =  parseAdditiveExpression();
        if (peek("!=") || peek("==") || peek(">") || peek("<")){
            String operator = tokens.get(0).getLiteral();
            match(operator);
            Ast.Expression rightOperand =  parseComparisonExpression();
            leftOperand = new Ast.Expression.Binary(operator, leftOperand, rightOperand);
        }
        return leftOperand;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expression parseAdditiveExpression() throws ParseException {
        Ast.Expression leftOperand =  parseMultiplicativeExpression();
        if (peek("-") || peek("+")){
            String operator = tokens.get(0).getLiteral();
            match(operator);
            Ast.Expression rightOperand =  parseComparisonExpression();
            leftOperand = new Ast.Expression.Binary(operator, leftOperand, rightOperand);
        }
        return leftOperand;
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expression parseMultiplicativeExpression() throws ParseException {
        Ast.Expression leftOperand =  parsePrimaryExpression();
        if (peek("^") || peek("/") || peek("*")){
            String operator = tokens.get(0).getLiteral();
            match(operator);
            Ast.Expression rightOperand =  parseComparisonExpression();
            leftOperand = new Ast.Expression.Binary(operator, leftOperand, rightOperand);
        }
        return leftOperand;
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expression parsePrimaryExpression() throws ParseException {
        if (peek("NIL")) {
            Ast.Expression output = new Ast.Expression.Literal(null);
            match("NIL");
            return output;
        } else if (peek("TRUE")) {
            Ast.Expression output = new Ast.Expression.Literal(true);
            match("TRUE");
            return output;
        } else if (peek("FALSE")) {
            Ast.Expression output = new Ast.Expression.Literal(false);
            match("FALSE");
            return output;
        } else if (peek(Token.Type.INTEGER)) {
            BigInteger number = new BigInteger(tokens.get(0).getLiteral());
            Ast.Expression output = new Ast.Expression.Literal(number);
            match(Token.Type.INTEGER);
            return output;
        } else if (peek(Token.Type.DECIMAL)) {
            BigDecimal number = new BigDecimal(tokens.get(0).getLiteral());
            Ast.Expression output = new Ast.Expression.Literal(number);
            match(Token.Type.DECIMAL);
            return output;
        } else if (peek(Token.Type.CHARACTER)) {
            String newString = tokens.get(0).getLiteral();
            newString = newString.replace("\'", "");
            Ast.Expression output = new Ast.Expression.Literal(newString.charAt(0));
            match(Token.Type.CHARACTER);
            return output;
        } else if (peek(Token.Type.STRING)) {
            String newString = tokens.get(0).getLiteral();
            newString = newString.replace("\"", "");
            newString = newString.replace("\\\\", "\\");
            newString = newString.replace("\\n", "\n");
            newString = newString.replace("\\b", "\b");
            newString = newString.replace("\\r", "\r");
            newString = newString.replace("\\t", "\t");
            Ast.Expression output = new Ast.Expression.Literal(newString);
            match(Token.Type.STRING);
            return output;
        } else if (peek("(")) {
            match("(");
            Ast.Expression output = new Ast.Expression.Group(parseExpression());
            if (match(")")) {
                return output;
            } else {
                throw new ParseException("Expected closing parenthesis", tokens.index);
            }
        } else if (peek(Token.Type.IDENTIFIER)) {
            String id = tokens.get(0).getLiteral();
            match(id);

            if (peek("(")) {
                match("(");
                List<Ast.Expression> expressions = new ArrayList<>();

                if (!peek(")")) {
                    do {
                        expressions.add(parseExpression());
                    } while (match(","));
                }

                if (match(")")) {
                    return new Ast.Expression.Function(id, expressions);
                } else {
                    throw new ParseException("Expected closing parenthesis", tokens.index);
                }
            } else if (peek("[")) {
                match("[");
                Ast.Expression output = null;

                if (!peek("]")) {
                    output = parseExpression();
                }

                match("]");  // Add this line
                return new Ast.Expression.Access(Optional.ofNullable(output), id);
            } else {
                Ast.Expression output = new Ast.Expression.Access(Optional.empty(), id);
                return output;
            }
        }

        throw new ParseException("Invalid expression", tokens.index);
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
    private boolean peek(Object... patterns) {
        for(int i = 0; i < patterns.length; i++){
            if(!tokens.has(i)){
                return false;
            } else if (patterns[i] instanceof Token.Type){
                if(patterns[i] != tokens .get(i).getType()){
                    return false;
                }
            } else if (patterns[i] instanceof String) {
                if(!patterns[i].equals(tokens.get(i).getLiteral())){
                    return false;
                }
            } else {
                throw new AssertionError("Invalid pattern object: " + patterns[i].getClass());
            }
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        boolean peek = peek(patterns);

        if(peek){
            for (int i = 0; i < patterns.length; i++){
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
