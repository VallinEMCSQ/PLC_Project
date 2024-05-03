package plc.project;

import javax.swing.plaf.nimbus.NimbusLookAndFeel;
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
        if(peek()){

        }
        if (this.tokens.has(0)){
            throw new ParseException("Invalid Source", tokens.index);
        }
        return new Ast.Source(globalList, functionList);
    }

    /**
     * Parses the {@code global} rule. This method should only be called if the
     * next tokens start a global, aka {@code LIST|VAL|VAR}.
     */
    public Ast.Global parseGlobal() throws ParseException {
        Ast.Global global = null;
        if(peek("LIST")){
            global = parseList();
        } else if (peek("VAR")) {
            match("VAR");
            global = parseMutable();
        } else if (peek("VAL")) {
            match("VAL");
            global = parseImmutable();
        } else {

            if (tokens.has(0)){
                throw new ParseException("Invalid Global", tokens.get(0).getIndex());
            }
            Token prevToken = tokens.get(-1);
            int tokenLength = prevToken.getLiteral().length();
            throw new ParseException("Invalid Global", prevToken.getIndex() + tokenLength);
        }
        match(";");
        return global;
    }

    /**
     * Parses the {@code list} rule. This method should only be called if the
     * next token declares a list, aka {@code LIST}.
     */
    public Ast.Global parseList() throws ParseException {
        match("LIST");
        if(peek(Token.Type.IDENTIFIER)){
            String identifier = tokens.get(0).getLiteral();
            match(identifier);
            List<Ast.Expression> values = new ArrayList<Ast.Expression>();
            if(match(":")) {
                String typeIdentifier = tokens.get(0).getLiteral();
                match(typeIdentifier);
                if (match("=")) {
                    if (match("[")) {
                        if (!match("]")) {

                            values.add(parseExpression());
                            while (match(",")) {
                                values.add(parseExpression());
                            }
                            if (match("]")) {
                                Ast.Expression.PlcList plcList = new Ast.Expression.PlcList(values);
                                return new Ast.Global(identifier, typeIdentifier, true, Optional.of(plcList));
                            }
                        }
                        Ast.Expression.PlcList plcList = new Ast.Expression.PlcList(values);
                        return new Ast.Global(identifier, typeIdentifier, true, Optional.of(plcList));
                    }
                }
            }
        }
        if (tokens.has(0)){
            throw new ParseException("Invalid List", tokens.get(0).getIndex());
        }
        Token prevToken = tokens.get(-1);
        int tokenLength = prevToken.getLiteral().length();
        throw new ParseException("Invalid List", prevToken.getIndex() + tokenLength);
    }

    /**
     * Parses the {@code mutable} rule. This method should only be called if the
     * next token declares a mutable global variable, aka {@code VAR}.
     */
    public Ast.Global parseMutable() throws ParseException {
        if (peek(Token.Type.IDENTIFIER)){
            String identifier = tokens.get(0).getLiteral();
            match(identifier);
            if(match(":")) {
                String typeIdentifier = tokens.get(0).getLiteral();
                match(typeIdentifier);
                if (match("=")) {
                    return new Ast.Global(identifier, typeIdentifier,true, Optional.of(parseExpression()));
                } else {
                    return new Ast.Global(identifier, typeIdentifier,true, Optional.empty());
                }
            }
            if (match("=")) {
                return new Ast.Global(identifier,true, Optional.of(parseExpression()));
            } else {
                return new Ast.Global(identifier,true, Optional.empty());
            }
        }
        if (tokens.has(0)){
            throw new ParseException("Invalid Mutable", tokens.get(0).getIndex());
        }
        Token prevToken = tokens.get(-1);
        int tokenLength = prevToken.getLiteral().length();
        throw new ParseException("Invalid Mutable", prevToken.getIndex() + tokenLength);
    }

    /**
     * Parses the {@code immutable} rule. This method should only be called if the
     * next token declares an immutable global variable, aka {@code VAL}.
     */
    public Ast.Global parseImmutable() throws ParseException {
        if(peek(Token.Type.IDENTIFIER)){
            String identifier = tokens.get(0).getLiteral();
            match(identifier);
            if(match(":")) {
                String typeIdentifier = tokens.get(0).getLiteral();
                match(typeIdentifier);
                if (match("=")) {
                    return new Ast.Global(identifier, typeIdentifier,false, Optional.of(parseExpression()));
                }
            }
            if (match("=")) {
                return new Ast.Global(identifier,false, Optional.of(parseExpression()));
            } else {
                return new Ast.Global(identifier,false, Optional.empty());
            }
        }
        if (tokens.has(0)){
            throw new ParseException("Invalid Immutable", tokens.get(0).getIndex());
        }
        Token prevToken = tokens.get(-1);
        int tokenLength = prevToken.getLiteral().length();
        throw new ParseException("Invalid Immutable", prevToken.getIndex() + tokenLength);
    }

    /**
     * Parses the {@code function} rule. This method should only be called if the
     * next tokens start a method, aka {@code FUN}.
     */
    public Ast.Function parseFunction() throws ParseException {
        if(!match("FUN")){
            if (tokens.has(0)){
                throw new ParseException("Invalid Function: Expected FUN", tokens.get(0).getIndex());
            }
            Token prevToken = tokens.get(-1);
            int tokenLength = prevToken.getLiteral().length();
            throw new ParseException("Invalid Function: Expected FUN", prevToken.getIndex() + tokenLength);
        }
        String identifier;
        String typeIdentifier = "Any";
        List<String> parameters = new ArrayList<>();
        List<String> typeParameters = new ArrayList<>();
        List<Ast.Statement> statements = new ArrayList<>();

        if (peek(Token.Type.IDENTIFIER)) {
            identifier = tokens.get(0).getLiteral();
            match(identifier);

            if (match("(")) {
                if (peek(Token.Type.IDENTIFIER)) {
                    do {
                        parameters.add(tokens.get(0).getLiteral());
                        match(Token.Type.IDENTIFIER);
                        if (match(":")) {
                            typeParameters.add(tokens.get(0).getLiteral());
                            match(Token.Type.IDENTIFIER);
                        }
                        else {
                            if (tokens.has(0)){
                                throw new ParseException("Expected colon", tokens.get(0).getIndex());
                            }
                            Token prevToken = tokens.get(-1);
                            int tokenLength = prevToken.getLiteral().length();
                            throw new ParseException("Expected colon", prevToken.getIndex() + tokenLength);
                        }
                    } while (match(","));
                }

                if (!match(")")) {
                    if (tokens.has(0)){
                        throw new ParseException("Invalid Function", tokens.get(0).getIndex());
                    }
                    Token prevToken = tokens.get(-1);
                    int tokenLength = prevToken.getLiteral().length();
                    throw new ParseException("Invalid Function", prevToken.getIndex() + tokenLength);
                }

                if (match(":")) {
                    typeIdentifier = tokens.get(0).getLiteral();
                    match(Token.Type.IDENTIFIER);
                }

                match("DO");

                while (!peek("END")) {
                    statements.add(parseStatement());
                }

                if (match("END")) {
                    if(typeIdentifier == null){
                        return new Ast.Function(identifier, parameters, typeParameters, Optional.of(typeIdentifier), statements);
                    }
                    return new Ast.Function(identifier, parameters, typeParameters, Optional.of(typeIdentifier), statements);
                } else {
                    if (tokens.has(0)){
                        throw new ParseException("Expected END", tokens.get(0).getIndex());
                    }
                    Token prevToken = tokens.get(-1);
                    int tokenLength = prevToken.getLiteral().length();
                    throw new ParseException("Expected END", prevToken.getIndex() + tokenLength);
                }
            }
        }
        if (tokens.has(0)){
            throw new ParseException("Invalid Function", tokens.get(0).getIndex());
        }
        Token prevToken = tokens.get(-1);
        int tokenLength = prevToken.getLiteral().length();
        throw new ParseException("Invalid Function", prevToken.getIndex() + tokenLength);
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
                if (peek(";")) {
                    statement = new Ast.Statement.Assignment(left, right);
                } else {
                    if (tokens.has(0)){
                        throw new ParseException("Expected semicolon", tokens.get(0).getIndex());
                    }
                    Token prevToken = tokens.get(-1);
                    int tokenLength = prevToken.getLiteral().length();
                    throw new ParseException("Expected semicolon", prevToken.getIndex() + tokenLength);
                }
            } else {
                statement = new Ast.Statement.Expression(left);
            }
            if(!match(";")){
                if (tokens.has(0)){
                    throw new ParseException("Expected semicolon", tokens.get(0).getIndex());
                }
                Token prevToken = tokens.get(-1);
                int tokenLength = prevToken.getLiteral().length();
                throw new ParseException("Expected semicolon", prevToken.getIndex() + tokenLength);
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
        boolean isList = false;
        match("LET");

        if (!peek(Token.Type.IDENTIFIER)) {
            if (tokens.has(0)){
                throw new ParseException("Expected identifier after LET", tokens.get(0).getIndex());
            }
            Token prevToken = tokens.get(-1);
            int tokenLength = prevToken.getLiteral().length();
            throw new ParseException("Expected identifier after LET", prevToken.getIndex() + tokenLength);
        }

        String name = tokens.get(0).getLiteral();
        match(Token.Type.IDENTIFIER);

        Optional<Ast.Expression> value = Optional.empty();
        Optional<String> typeName = Optional.empty();

        if (match(":")) {
            isList = true;
            if (peek(Token.Type.IDENTIFIER)) {
                typeName = Optional.of(tokens.get(0).getLiteral());
                match(Token.Type.IDENTIFIER);
            } else {
                if (tokens.has(0)){
                    throw new ParseException("Expected type name", tokens.get(0).getIndex());
                }
                Token prevToken = tokens.get(-1);
                int tokenLength = prevToken.getLiteral().length();
                throw new ParseException("Expected type name", prevToken.getIndex() + tokenLength);
            }
        }

        if (match("=")) {
            value = Optional.of(parseExpression());
        }

        if (!match(";")) {
            if (tokens.has(0)){
                throw new ParseException("Expected semicolon", tokens.get(0).getIndex());
            }
            Token prevToken = tokens.get(-1);
            int tokenLength = prevToken.getLiteral().length();
            throw new ParseException("Expected semicolon", prevToken.getIndex() + tokenLength);
        }

        if (isList){
            return new Ast.Statement.Declaration(name, typeName, Optional.empty());
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
                if (tokens.has(0)){
                    throw new ParseException("Expected END", tokens.get(0).getIndex());
                }
                Token prevToken = tokens.get(-1);
                int tokenLength = prevToken.getLiteral().length();
                throw new ParseException("Expected END", prevToken.getIndex() + tokenLength);
            }
        } else {
            if (tokens.has(0)){
                throw new ParseException("Expected DO", tokens.get(0).getIndex());
            }
            Token prevToken = tokens.get(-1);
            int tokenLength = prevToken.getLiteral().length();
            throw new ParseException("Expected DO", prevToken.getIndex() + tokenLength);
        }
    }

    /**
     * Parses a switch statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a switch statement, aka
     * {@code SWITCH}.
     */
    public Ast.Statement.Switch parseSwitchStatement() throws ParseException {
        if (!match("SWITCH")) {
            if (tokens.has(0)){
                throw new ParseException("Expected SWITCH", tokens.get(0).getIndex());
            }
            Token prevToken = tokens.get(-1);
            int tokenLength = prevToken.getLiteral().length();
            throw new ParseException("Expected SWITCH", prevToken.getIndex() + tokenLength);
        }
        Ast.Expression expression = parseExpression();
        List<Ast.Statement.Case> cases = new ArrayList<Ast.Statement.Case>();
        while (peek("CASE") || peek("DEFAULT")){
            Ast.Statement.Case caseStatement = parseCaseStatement();
            cases.add(caseStatement);
        }
        if (match("END")){
            return new Ast.Statement.Switch(expression, cases);
        }
        if (tokens.has(0)){
            throw new ParseException("Expected END", tokens.get(0).getIndex());
        }
        Token prevToken = tokens.get(-1);
        int tokenLength = prevToken.getLiteral().length();
        throw new ParseException("Invalid Switch Statement", prevToken.getIndex() + tokenLength);
    }

    /**
     * Parses a case or default statement block from the {@code switch} rule. 
     * This method should only be called if the next tokens start the case or 
     * default block of a switch statement, aka {@code CASE} or {@code DEFAULT}.
     */
    public Ast.Statement.Case parseCaseStatement() throws ParseException {
        Ast.Statement.Case caseStatement = null;
        if (match("CASE")){
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
            while (!peek("END")){
                statements.add(parseStatement());
            }
            caseStatement = new Ast.Statement.Case(Optional.empty(), statements);
        } else {
            if (tokens.has(0)){
                throw new ParseException("Invalid Case Statement", tokens.get(0).getIndex());
            }
            Token prevToken = tokens.get(-1);
            int tokenLength = prevToken.getLiteral().length();
            throw new ParseException("Invalid Case Statement", prevToken.getIndex() + tokenLength);
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
        if (tokens.has(0)){
            throw new ParseException("Invalid While Statement", tokens.get(0).getIndex());
        }
        Token prevToken = tokens.get(-1);
        int tokenLength = prevToken.getLiteral().length();
        throw new ParseException("Invalid While Statement", prevToken.getIndex() + tokenLength);
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
        if (tokens.has(0)){
            throw new ParseException("Invalid Return", tokens.get(0).getIndex());
        }
        Token prevToken = tokens.get(-1);
        int tokenLength = prevToken.getLiteral().length();
        throw new ParseException("Invalid Return", prevToken.getIndex() + tokenLength);

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
                if(tokens.has(0)) {
                    throw  new ParseException("Expected Closing Parenthesis", tokens.get(0).getIndex());
                }
                Token prevToken = tokens.get(-1);
                int tokenLength = prevToken.getLiteral().length();
                throw new ParseException("Expected Closing Parenthesis", prevToken.getIndex() + tokenLength);
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
                    if(tokens.has(0)) {
                        throw  new ParseException("Expected closing parenthesis", tokens.get(0).getIndex());
                    }
                    Token prevToken = tokens.get(-1);
                    int tokenLength = prevToken.getLiteral().length();
                    throw new ParseException("Expected closing parenthesis", prevToken.getIndex() + tokenLength);
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

        if (tokens.has(0)){
            throw new ParseException("Invalid expression", tokens.get(0).getIndex());
        }
        Token prevToken = tokens.get(-1);
        int tokenLength = prevToken.getLiteral().length();
        throw new ParseException("Invalid expression", prevToken.getIndex() + tokenLength);
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
