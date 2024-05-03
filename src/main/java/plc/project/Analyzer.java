package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import java.util.Optional;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {

    public Scope scope;
    private Ast.Function function;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        for (Ast.Global global : ast.getGlobals()) {
            visit(global);
        }
        for (Ast.Function function : ast.getFunctions()) {
            visit(function);
        }
        boolean hasMainFunction = false;
        for (Ast.Function function : ast.getFunctions()) {
            if (function.getName().equals("main") && function.getParameterTypeNames().isEmpty()) {
                hasMainFunction = true;
                if (!function.getReturnTypeName().isPresent() || !function.getReturnTypeName().get().equals("Integer")) {
                    throw new RuntimeException("Main function must have an Integer return type.");
                }
                break;
            }
        }
        if (!hasMainFunction) {
            throw new RuntimeException("Main function not found.");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Global ast) {
        if (ast.getValue().isPresent()) {
            if(ast.getValue().get().getClass() == Ast.Expression.PlcList.class){
                Ast.Expression.PlcList temp = (Ast.Expression.PlcList)ast.getValue().get();
                temp.setType(Environment.getType(ast.getTypeName()));
            }
            visit(ast.getValue().get());
            requireAssignable(Environment.getType(ast.getTypeName()), ast.getValue().get().getType());
        }
        scope.defineVariable(ast.getName(), ast.getName(), Environment.getType(ast.getTypeName()), true, Environment.NIL);
        ast.setVariable(scope.lookupVariable(ast.getName()));
        return null;
        /*String name = ast.getName();
        Optional<String> typeName = Optional.ofNullable(ast.getTypeName());
        Optional<Ast.Expression> value = ast.getValue();

        if (!typeName.isPresent()) {
            throw new RuntimeException("Missing type for global variable: " + name);
        }

        String typeNameString = typeName.get();
        Environment.Type type;


        switch (typeNameString) {
            case "Any":
                type = Environment.Type.ANY;
                break;
            case "Nil":
                type = Environment.Type.NIL;
                break;
            case "Comparable":
                type = Environment.Type.COMPARABLE;
                break;
            case "Boolean":
                type = Environment.Type.BOOLEAN;
                break;
            case "Integer":
                type = Environment.Type.INTEGER;
                break;
            case "Decimal":
                type = Environment.Type.DECIMAL;
                break;
            case "Character":
                type = Environment.Type.CHARACTER;
                break;
            case "String":
                type = Environment.Type.STRING;
                break;
            default:
                throw new RuntimeException("Unknown type: " + typeNameString);
        }

        // Initialize the variable
        Environment.Variable variable = scope.defineVariable(name, name, type, true, Environment.NIL); // Set jvmName to name here


        value.ifPresent(expression -> {
            visit(expression);
            requireAssignable(variable.getType(), expression.getType());
        });

        ast.setVariable(variable);

        return null;*/
    }

    @Override
    public Void visit(Ast.Function ast) {
        String name = ast.getName();
        String jvmName = ast.getName();
        List<String> parameterTypeNames = ast.getParameterTypeNames();
        Optional<String> returnTypeName = ast.getReturnTypeName();


        List<Environment.Type> parameterTypes = parameterTypeNames.stream()
                .map(Environment::getType)
                .collect(Collectors.toList());


        Environment.Type returnType = returnTypeName.map(Environment::getType)
                .orElse(Environment.getType("Nil"));


        Environment.Function function = new Environment.Function(name, jvmName, parameterTypes, returnType, args -> Environment.NIL);
        scope.defineFunction(name, jvmName, parameterTypes, returnType, args -> Environment.NIL);


        ast.setFunction(function);


        try {
            scope = new Scope(scope);
            for (int i = 0; i < parameterTypes.size(); i++) {
                String paramName = "param" + i; // Assuming parameter names are not explicitly provided
                Environment.Type paramType = parameterTypes.get(i);
                scope.defineVariable(paramName, paramName, paramType, true, Environment.NIL);
            }

            scope.defineVariable("Return", "Return", returnType, true, Environment.NIL);

            for (Ast.Statement statement : ast.getStatements()) {
                if (statement instanceof Ast.Statement.Expression) {
                    visit((Ast.Statement.Expression) statement);
                } else if (statement instanceof Ast.Statement.Declaration) {
                    visit((Ast.Statement.Declaration) statement);
                } else if (statement instanceof Ast.Statement.Assignment) {
                    visit((Ast.Statement.Assignment) statement);
                } else if (statement instanceof Ast.Statement.If) {
                    visit((Ast.Statement.If) statement);
                } else if (statement instanceof Ast.Statement.Switch) {
                    visit((Ast.Statement.Switch) statement);
                } else if (statement instanceof Ast.Statement.While) {
                    visit((Ast.Statement.While) statement);
                } else if (statement instanceof Ast.Statement.Return) {
                    visit((Ast.Statement.Return) statement);
                } else {
                    throw new RuntimeException("Unknown statement type: " + statement.getClass().getName());
                }
            }
        } finally {
            scope = scope.getParent();
        }

        return null;  // TODO
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        try {
            visit(ast.getExpression());
            if (!(ast.getExpression() instanceof Ast.Expression.Function)) {
                throw new RuntimeException("Not function type!");
            }
        } catch (RuntimeException e) {
            throw new RuntimeException("Error in visit method of Ast.Statement.Expression: " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        Environment.Type variableType = null;
        Optional<Ast.Expression> value = ast.getValue();
        if(ast.getTypeName().isPresent()){
            variableType = Environment.getType((String)ast.getTypeName().get());
        }
        else if (value.isPresent()) {
            visit((Ast.Expression)value.get());
            variableType = value.get().getType();
        }
        else {
            throw new RuntimeException("No type or value provided for declaration.");
        }

        if(value.isPresent()){
            requireAssignable(variableType, value.get().getType());
        }

        scope.defineVariable(ast.getName(), ast.getName(), variableType, true, Environment.NIL);
        ast.setVariable(scope.lookupVariable(ast.getName()));

        return null;

    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        if(!(ast.getReceiver() instanceof Ast.Expression.Access)){
            throw new RuntimeException("Not Access type!");

        }
        visit(ast.getReceiver());
        visit(ast.getValue());
        requireAssignable(ast.getReceiver().getType(), ast.getValue().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        visit(ast.getCondition());
        if(ast.getThenStatements().isEmpty()){
            throw new RuntimeException("Then statements are empty!");
        }
        if(ast.getCondition().getType() != Environment.Type.BOOLEAN){
            throw new RuntimeException("Condition is not boolean!");
        }

        for(Ast.Statement statement : ast.getThenStatements()){
            try {
                scope = new Scope(scope);
                visit(statement);
            } finally {
                scope = scope.getParent();
            }
        }

        for(Ast.Statement statement : ast.getElseStatements()){
            try {
                scope = new Scope(scope);
                visit(statement);
            } finally {
                scope = scope.getParent();
            }
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {
        // Visit the condition expression
        visit(ast.getCondition());


        Environment.Type conditionType = ast.getCondition().getType();
        if (conditionType == null) {
            throw new RuntimeException("Type of condition variable is uninitialized");
        }


        boolean defaultCaseVisited = false;
        boolean isConditionMatched = false;
        for (Ast.Statement.Case caseStatement : ast.getCases()) {

            if (!caseStatement.getValue().isPresent()) {
                if (defaultCaseVisited) {
                    throw new RuntimeException("Multiple default cases found");
                }
                defaultCaseVisited = true;
            } else {

                Ast.Expression caseValue = caseStatement.getValue().get();
                visit(caseValue); // Visit the case value expression
                Environment.Type caseValueType = caseValue.getType();
                if (caseValueType != conditionType) {
                    throw new RuntimeException("Type mismatch between switch condition and case value");
                }
            }

            try {

                Scope caseScope = new Scope(scope);


                caseScope.defineVariable("switchCondition", "switchCondition", conditionType, false, Environment.NIL);


                for (Ast.Statement statement : caseStatement.getStatements()) {
                    visit(statement);
                }
            } catch (RuntimeException e) {
                throw new RuntimeException("Error processing case statement: " + e.getMessage());
            }
        }


        if (!isConditionMatched && !defaultCaseVisited) {
            throw new RuntimeException("No matching case found for switch condition");
        }

        return null;   // TODO
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {

        try {
            scope = new Scope(scope);

            for (Ast.Statement statement : ast.getStatements()) {
                visit(statement);
            }

        } finally {
            scope = scope.getParent();
        }

        return null;  // TODO
    }

    @Override
    public Void visit(Ast.Statement.While ast) {

        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());

        try {
            scope = new Scope(scope);
            for (Ast.Statement statement : ast.getStatements()) {
                visit(statement);
            }
        } finally {

            scope = scope.getParent();
        }

        return null;  // TODO
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        try {
            visit(ast.getValue());
            Environment.Type type = scope.lookupVariable("Return").getType();
            requireAssignable(type, ast.getValue().getType());
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        try {
            if (ast.getLiteral() instanceof Boolean) {
                ast.setType(Environment.Type.BOOLEAN);
                requireAssignable(Environment.Type.BOOLEAN, Environment.Type.BOOLEAN);
            } else if (ast.getLiteral() instanceof BigInteger) {
                BigInteger value = (BigInteger) ast.getLiteral();
                if (value.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0 || value.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0) {
                    throw new RuntimeException("BigInteger literal out of range: " + value);
                }
                ast.setType(Environment.Type.INTEGER);
                requireAssignable(Environment.Type.INTEGER, Environment.Type.INTEGER);
            } else if (ast.getLiteral() instanceof BigDecimal) {
                ast.setType(Environment.Type.DECIMAL);
                requireAssignable(Environment.Type.DECIMAL, Environment.Type.DECIMAL);
            } else if (ast.getLiteral() instanceof Character) {
                ast.setType(Environment.Type.CHARACTER);
                requireAssignable(Environment.Type.CHARACTER, Environment.Type.CHARACTER);
            } else if (ast.getLiteral() instanceof String) {
                ast.setType(Environment.Type.STRING);
                requireAssignable(Environment.Type.STRING, Environment.Type.STRING);
            } else {
                throw new RuntimeException("Unknown literal type: " + ast.getLiteral().getClass());
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        try {
            visit(ast.getExpression());
            try {
                if (ast.getExpression().getClass() != Ast.Expression.Binary.class) {
                    // System.out.println("NOT BINARY TYPE!");
                    throw new RuntimeException("Not binary type!");
                }
            }
            catch (RuntimeException r) {
                // System.out.println("NOT BINARY TYPE!");
                throw new RuntimeException("Not binary type!");
            }
        } catch (RuntimeException r) {
            // System.out.println("Ast.Group:");
            // System.out.println(r);
            throw new RuntimeException(r);
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        try {
            visit(ast.getLeft());
            visit(ast.getRight());

            if (ast.getOperator().equals("+")) {
                if (ast.getLeft().getType() == Environment.Type.STRING && ast.getRight().getType() == Environment.Type.STRING) {
                    ast.setType(Environment.Type.STRING);
                } else if (ast.getLeft().getType() == Environment.Type.INTEGER && ast.getRight().getType() == Environment.Type.INTEGER) {
                    ast.setType(Environment.Type.INTEGER);
                } else if (ast.getLeft().getType() == Environment.Type.STRING || ast.getRight().getType() == Environment.Type.STRING) {
                    ast.setType(Environment.Type.STRING); // Added logic for string concatenation
                } else {
                    throw new RuntimeException("String concatenation requires string operands.");
                }
            } else if (ast.getOperator().equals("&&")) {
                if (ast.getLeft().getType() == Environment.Type.BOOLEAN && ast.getRight().getType() == Environment.Type.BOOLEAN) {
                    ast.setType(Environment.Type.BOOLEAN);
                } else {
                    throw new RuntimeException("Logical AND operation requires boolean operands.");
                }
            } else if (ast.getOperator().equals("==") || ast.getOperator().equals("!=") || ast.getOperator().equals("<") || ast.getOperator().equals("<=") || ast.getOperator().equals(">") || ast.getOperator().equals(">=")) {
                if (ast.getLeft().getType() == Environment.Type.INTEGER && ast.getRight().getType() == Environment.Type.INTEGER) {
                    ast.setType(Environment.Type.BOOLEAN);
                } else if (ast.getLeft().getType() == Environment.Type.DECIMAL && ast.getRight().getType() == Environment.Type.DECIMAL) {
                    ast.setType(Environment.Type.BOOLEAN);
                } else if (ast.getLeft().getType() == Environment.Type.CHARACTER && ast.getRight().getType() == Environment.Type.CHARACTER) {
                    ast.setType(Environment.Type.BOOLEAN);
                } else if (ast.getLeft().getType() == Environment.Type.STRING && ast.getRight().getType() == Environment.Type.STRING) {
                    ast.setType(Environment.Type.BOOLEAN);
                } else {
                    throw new RuntimeException("Comparison operations require comparable operands.");
                }
            } else {
                // Handle arithmetic operations
                if (ast.getLeft().getType() == Environment.Type.INTEGER && ast.getRight().getType() == Environment.Type.INTEGER) {
                    ast.setType(Environment.Type.INTEGER);
                } else if (ast.getLeft().getType() == Environment.Type.DECIMAL && ast.getRight().getType() == Environment.Type.DECIMAL) {
                    ast.setType(Environment.Type.DECIMAL);
                } else {
                    throw new RuntimeException("Arithmetic operations require numeric operands.");
                }
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        try {
            Environment.Variable variable = scope.lookupVariable(ast.getName());
            ast.setVariable(variable);
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        try {
            Environment.Function function = scope.lookupFunction(ast.getName(), ast.getArguments().size());
            ast.setFunction(function);
            for (Ast.Expression expression : ast.getArguments()) {
                visit(expression);
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        Environment.Type elementType = ast.getType();
        List<Ast.Expression> elements = ast.getValues();
        for (Ast.Expression element : elements) {
            visit(element);
            requireAssignable(elementType, element.getType());
        }
        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        try {
            if (target != type && target != Environment.Type.ANY && target != Environment.Type.COMPARABLE) {
                throw new RuntimeException("Not matching types!");
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
    }

}
