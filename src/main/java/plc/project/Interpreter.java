package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


import java.math.MathContext;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        for (Ast.Global global : ast.getGlobals()) {
            visit(global);
        }
        for (Ast.Function function : ast.getFunctions()) {
            visit(function);
        }

        List<Environment.PlcObject> arguments = new ArrayList<Environment.PlcObject>();
        Environment.PlcObject object = scope.lookupFunction("main", 0).invoke(arguments);
        return object;

        /*try {
            Environment.Function mainFunction = scope.lookupFunction("main", 0);

            List<Environment.PlcObject> arguments = new ArrayList<>();

            Environment.PlcObject result = mainFunction.invoke(arguments);

            return result;
        } catch (Exception e) {
            return Environment.NIL;
        }*/
    }

    @Override
    public Environment.PlcObject visit(Ast.Global ast) {
        if (ast.getValue().isPresent()) {
            scope.defineVariable(ast.getName(), ast.getMutable(), visit(ast.getValue().get()));
        } else {
            scope.defineVariable(ast.getName(), ast.getMutable(), Environment.NIL);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Function ast) {
        Scope oldScope = scope;
        scope.defineFunction(ast.getName(), ast.getParameters().size(), args -> {
            scope = new Scope(oldScope);

            try {
                for (int i = 0; i < ast.getParameters().size(); i++) {
                    scope.defineVariable(ast.getParameters().get(i), true, args.get(i));
                }
                ast.getStatements().forEach(this::visit);
            } catch (Return returnValue) {
                return returnValue.value;
            } finally {
                scope = scope.getParent();
            }
            return Environment.NIL;
        });
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Declaration ast) {
        //Optional optional = ast.getValue();
        //Optional<Ast.Expression> op1 = ast.getValue();
        //Boolean present = optional.isPresent();

        if (ast.getValue().isPresent()){
            Ast.Expression expr = ast.getValue().get();
            scope.defineVariable(ast.getName(), true, visit(expr));
        } else {
            scope.defineVariable(ast.getName(), true, Environment.NIL);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Assignment ast) {

        Environment.PlcObject value = visit(ast.getValue());

        if (ast.getReceiver() instanceof Ast.Expression.Access) {
            Ast.Expression.Access access = (Ast.Expression.Access) ast.getReceiver();
            String variableName = access.getName();

            Environment.Variable variable = scope.lookupVariable(variableName);

            if (!variable.getMutable()) {
                throw new RuntimeException("Cannot assign to immutable variable: " + variableName);
            }

            if (variable.getValue().getValue() instanceof List && access.getOffset().isPresent()) {
                List<Object> list = requireType(List.class, variable.getValue());
                Object offsetObject = visit(access.getOffset().get()).getValue();
                int offset;
                if (offsetObject instanceof Integer) {
                    offset = (int) offsetObject;
                } else if (offsetObject instanceof BigInteger) {
                    offset = ((BigInteger) offsetObject).intValueExact();
                } else {
                    throw new RuntimeException("Index expression must evaluate to an Integer or a BigInteger");
                }
                if (offset < 0 || offset >= list.size()) {
                    throw new IndexOutOfBoundsException("Index out of bounds: " + offset);
                }
                list.set(offset, value.getValue());
                variable.setValue(Environment.create(list));
            } else {
                variable.setValue(value);
            }
        } else {
            throw new RuntimeException("Unsupported assignment target: " + ast.getReceiver());
        }
        return Environment.NIL;
    }


    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {
        Environment.PlcObject conditionValue = visit(ast.getCondition());

        if (!(conditionValue.getValue() instanceof Boolean)) {
            throw new RuntimeException("If statement condition must evaluate to a boolean value.");
        }

        List<Ast.Statement> branch = ((boolean) conditionValue.getValue()) ? ast.getThenStatements() : ast.getElseStatements();

        for (Ast.Statement statement : branch) {
            visit(statement);
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Switch ast) {
        Environment.PlcObject conditionValue = visit(ast.getCondition());

        boolean caseMatched = false;

        for (Ast.Statement.Case switchCase : ast.getCases()) {
            if (switchCase.getValue().isPresent()) {
                Environment.PlcObject caseValue = visit(switchCase.getValue().get());
                if (caseValue.getValue().equals(conditionValue.getValue())) {

                    for (Ast.Statement statement : switchCase.getStatements()) {
                        visit(statement);
                    }
                    caseMatched = true;
                    break;
                }
            } else {
                for (Ast.Statement statement : switchCase.getStatements()) {
                    visit(statement);
                }
                caseMatched = true;
                break;
            }
        }

        if (!caseMatched) {
            for (Ast.Statement.Case switchCase : ast.getCases()) {
                if (!switchCase.getValue().isPresent()) {
                    for (Ast.Statement statement : switchCase.getStatements()) {
                        visit(statement);
                    }
                    break;
                }
            }
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Case ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.While ast) {
        //throw new UnsupportedOperationException(); //TODO (in lecture)
        while (requireType(Boolean.class, visit(ast.getCondition()))) {
            try{
                scope = new Scope(scope);
                ast.getStatements().forEach(this::visit);
            } finally {
                scope = scope.getParent();
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Return ast) {
        Environment.PlcObject value = visit(ast.getValue());
        throw new Return(value);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Literal ast) {
        if (ast.getLiteral() == null){
            return Environment.NIL;
        }
        return Environment.create(ast.getLiteral());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Group ast) {
        // Visit the grouped expression and return its value
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Binary ast) {
        String operator = ast.getOperator();
        if(operator.equals(">") || operator.equals("<")){
            // Get left and right values
            Environment.PlcObject rightValue = visit(ast.getRight());
            Environment.PlcObject leftValue = visit(ast.getLeft());

            // Check if both are comparable
            requireType(leftValue.getValue().getClass(), rightValue);
            requireType(Comparable.class, rightValue);
            requireType(Comparable.class, leftValue);

            // Set as comparable objects
            Comparable<Object> right = (Comparable<Object>) rightValue.getValue();
            Comparable<Object> left = (Comparable<Object>) leftValue.getValue();

            // Compare the values
            int result = left.compareTo(right);

            // Return the result
            if(operator.equals(">")){
                if (result > 0){
                    return Environment.create(true);
                } else {
                    return Environment.create(false);
                }
            } else {
                if (result < 0){
                    return Environment.create(true);
                } else {
                    return Environment.create(false);
                }
            }

        } else if (operator.equals("+")) {
            Environment.PlcObject rightValue = visit(ast.getRight());
            Environment.PlcObject leftValue = visit(ast.getLeft());

            if (rightValue.getValue().getClass() == String.class || leftValue.getValue().getClass() == String.class){
                return Environment.create(leftValue.getValue().toString() + rightValue.getValue().toString());
            } else if (rightValue.getValue().getClass() == BigDecimal.class){
                requireType(BigDecimal.class, leftValue);
                return Environment.create(((BigDecimal) leftValue.getValue()).add((BigDecimal) rightValue.getValue()));
            } else if (rightValue.getValue().getClass() == BigInteger.class){
                requireType(BigInteger.class, leftValue);
                return Environment.create(((BigInteger) leftValue.getValue()).add((BigInteger) rightValue.getValue()));
            } else {
                throw new RuntimeException("Unsupported operand types for operator +");
            }
        } else if (operator.equals("^")) {
            Environment.PlcObject rightValue = visit(ast.getRight());
            Environment.PlcObject leftValue = visit(ast.getLeft());

            requireType(BigInteger.class, rightValue);

            if (leftValue.getValue().getClass() == BigDecimal.class){
                return Environment.create(((BigDecimal) leftValue.getValue()).pow(((BigInteger) rightValue.getValue()).intValue(), MathContext.DECIMAL64));
            } else if (leftValue.getValue().getClass() == BigInteger.class){
                return Environment.create(((BigInteger) leftValue.getValue()).pow(((BigInteger) rightValue.getValue()).intValue()));
            } else {
                throw new RuntimeException("Unsupported operand types for operator ^");
            }
        } else if (operator.equals("-")) {
            Environment.PlcObject rightValue = visit(ast.getRight());
            Environment.PlcObject leftValue = visit(ast.getLeft());

            if (rightValue.getValue().getClass() == BigDecimal.class){
                requireType(BigDecimal.class, leftValue);
                return Environment.create(((BigDecimal) leftValue.getValue()).subtract((BigDecimal) rightValue.getValue()));
            } else if (rightValue.getValue().getClass() == BigInteger.class){
                requireType(BigInteger.class, leftValue);
                return Environment.create(((BigInteger) leftValue.getValue()).subtract((BigInteger) rightValue.getValue()));
            } else {
                throw new RuntimeException("Unsupported operand types for operator -");
            }
        } else if (operator.equals("*")) {
            Environment.PlcObject rightValue = visit(ast.getRight());
            Environment.PlcObject leftValue = visit(ast.getLeft());

            if (rightValue.getValue().getClass() == BigDecimal.class){
                requireType(BigDecimal.class, leftValue);
                return Environment.create(((BigDecimal) leftValue.getValue()).multiply((BigDecimal) rightValue.getValue()));
            } else if (rightValue.getValue().getClass() == BigInteger.class){
                requireType(BigInteger.class, leftValue);
                return Environment.create(((BigInteger) leftValue.getValue()).multiply((BigInteger) rightValue.getValue()));
            } else {
                throw new RuntimeException("Unsupported operand types for operator *");
            }
        } else if (operator.equals("/")) {
            Environment.PlcObject rightValue = visit(ast.getRight());
            Environment.PlcObject leftValue = visit(ast.getLeft());

            if (rightValue.getValue().equals(BigDecimal.ZERO) || rightValue.getValue().equals(BigInteger.ZERO)){
                throw new RuntimeException("Division by zero");
            }

            if(rightValue.getValue().getClass() == BigDecimal.class){
                requireType(BigDecimal.class, leftValue);
                return Environment.create(((BigDecimal) leftValue.getValue()).divide((BigDecimal) rightValue.getValue(), RoundingMode.HALF_EVEN));
            } else if (rightValue.getValue().getClass() == BigInteger.class){
                requireType(BigInteger.class, leftValue);
                return Environment.create(((BigInteger) leftValue.getValue()).divide((BigInteger) rightValue.getValue()));
            } else {
                throw new RuntimeException("Unsupported operand types for operator /");

            }
        } else if (operator.equals("==") || operator.equals("!=")) {
            Environment.PlcObject rightValue = visit(ast.getRight());
            Environment.PlcObject leftValue = visit(ast.getLeft());

            if (operator.equals("==")){
                return Environment.create(leftValue.getValue().equals(rightValue.getValue()));
            } else {
                return Environment.create(!leftValue.getValue().equals(rightValue.getValue()));
            }
        } else if (operator.equals("&&") || operator.equals("||")) {
            Environment.PlcObject leftValue = visit(ast.getLeft());
            requireType(Boolean.class, leftValue);

            if(leftValue.getValue().equals(true) && operator.equals("||")){
                return Environment.create(true);
            } else if(leftValue.getValue().equals(false) && operator.equals("&&")){
                return Environment.create(false);
            } else{
                Environment.PlcObject rightValue = visit(ast.getRight());
                requireType(Boolean.class, rightValue);
                if (operator.equals("&&")){
                    return Environment.create((boolean) leftValue.getValue() && (boolean) rightValue.getValue());
                } else {
                    return Environment.create((boolean) leftValue.getValue() || (boolean) rightValue.getValue());
                }
            }
        } else {
            throw new RuntimeException("Unsupported binary expression");
        }
        /*Environment.PlcObject leftValue = visit(ast.getLeft());

        if (ast.getOperator().equals("||") && (boolean) leftValue.getValue()) {
            return leftValue;
        }

        Environment.PlcObject rightValue = visit(ast.getRight());

        switch (ast.getOperator()) {
            case "+":
                if (leftValue.getValue() instanceof BigInteger && rightValue.getValue() instanceof BigInteger) {
                    return Environment.create(((BigInteger) leftValue.getValue()).add((BigInteger) rightValue.getValue()));
                } else if (leftValue.getValue() instanceof String && rightValue.getValue() instanceof String) {
                    return Environment.create((String) leftValue.getValue() + (String) rightValue.getValue());
                } else if (leftValue.getValue() instanceof BigDecimal && rightValue.getValue() instanceof BigDecimal) {
                    return Environment.create(((BigDecimal) leftValue.getValue()).add((BigDecimal) rightValue.getValue()));
                } else {
                    throw new UnsupportedOperationException("Unsupported operand types for operator +");
                }
            case "-":
                if (leftValue.getValue() instanceof BigInteger && rightValue.getValue() instanceof BigInteger) {
                    return Environment.create(((BigInteger) leftValue.getValue()).subtract((BigInteger) rightValue.getValue()));
                } else {
                    throw new UnsupportedOperationException("Unsupported operand types for operator -");
                }
            case "*":
                if (leftValue.getValue() instanceof BigInteger && rightValue.getValue() instanceof BigInteger) {
                    return Environment.create(((BigInteger) leftValue.getValue()).multiply((BigInteger) rightValue.getValue()));
                } else {
                    throw new UnsupportedOperationException("Unsupported operand types for operator *");
                }
            case "/":
                if (leftValue.getValue() instanceof BigDecimal && rightValue.getValue() instanceof BigDecimal) {
                    BigDecimal left = (BigDecimal) leftValue.getValue();
                    BigDecimal right = (BigDecimal) rightValue.getValue();
                    return Environment.create(left.divide(right, MathContext.DECIMAL64).setScale(1, BigDecimal.ROUND_HALF_UP));
                } else {
                    throw new UnsupportedOperationException("Unsupported operand types for operator /");
                }
            case "<":
                if (leftValue.getValue() instanceof BigInteger && rightValue.getValue() instanceof BigInteger) {
                    return Environment.create(((BigInteger) leftValue.getValue()).compareTo((BigInteger) rightValue.getValue()) < 0);
                } else {
                    throw new UnsupportedOperationException("Unsupported operand types for operator <");
                }
            case "==":
                return Environment.create(leftValue.getValue().equals(rightValue.getValue()));
            case "&&":
                return Environment.create((boolean) leftValue.getValue() && (boolean) rightValue.getValue());
            case "||":
                return Environment.create((boolean) leftValue.getValue() || (boolean) rightValue.getValue());
            case "=":
                // Assignment operation for list
                if (ast.getLeft() instanceof Ast.Expression.Access) {
                    Ast.Expression.Access access = (Ast.Expression.Access) ast.getLeft();
                    Environment.Variable variable = scope.lookupVariable(access.getName());
                    List<Object> list = requireType(List.class, variable.getValue());

                    if (access.getOffset().isPresent()) {
                        int offset = requireType(Integer.class, visit(access.getOffset().get()));
                        if (offset < 0 || offset >= list.size()) {
                            throw new IndexOutOfBoundsException("Index out of bounds: " + offset);
                        }
                        list.set(offset, rightValue.getValue());
                    } else {
                        throw new UnsupportedOperationException("List assignment requires an index.");
                    }
                    return Environment.NIL;
                } else {
                    throw new UnsupportedOperationException("Unsupported assignment target: " + ast.getLeft());
                }
            default:
                throw new UnsupportedOperationException("Unsupported binary operator: " + ast.getOperator());
        }*/
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Access ast) {
        String variableName = ast.getName();
        Optional<Ast.Expression> indexExpression = ast.getOffset();

        // Resolve the variable
        Environment.Variable variable = scope.lookupVariable(variableName);

        // If the variable value is a list and an index is specified
        if (variable.getValue().getValue() instanceof List && indexExpression.isPresent()) {
            // Evaluate the index expression
            Environment.PlcObject indexValue = visit(indexExpression.get());
            // Check if the index value is an Integer or a BigInteger
            if (indexValue.getValue() instanceof Integer) {
                int index = (int) indexValue.getValue();
                List<Object> list = (List<Object>) variable.getValue().getValue();
                // Check if the index is within bounds
                if (index >= 0 && index < list.size()) {
                    // Return the value at the specified index in the list
                    return new Environment.PlcObject(null, list.get(index));
                } else {
                    throw new RuntimeException("Index out of bounds for list variable: " + variableName);
                }
            } else if (indexValue.getValue() instanceof BigInteger) {
                BigInteger index = (BigInteger) indexValue.getValue();
                List<Object> list = (List<Object>) variable.getValue().getValue();
                // Check if the index is within bounds
                if (index.compareTo(BigInteger.ZERO) >= 0 && index.compareTo(BigInteger.valueOf(list.size())) < 0) {
                    // Return the value at the specified index in the list
                    return new Environment.PlcObject(null, list.get(index.intValue()));
                } else {
                    throw new RuntimeException("Index out of bounds for list variable: " + variableName);
                }
            } else {
                throw new RuntimeException("Index expression must evaluate to an Integer or a BigInteger");
            }
        } else {
            // Return the variable value if no index is specified or if the variable is not a list
            return variable.getValue();
        }
    }


    @Override
    public Environment.PlcObject visit(Ast.Expression.Function ast) {

        Environment.Function function = scope.lookupFunction(ast.getName(), ast.getArguments().size());

        List<Environment.PlcObject> arguments = ast.getArguments().stream()
                .map(this::visit)
                .collect(Collectors.toList());

        return function.invoke(arguments);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.PlcList ast) {
        // Create a new list
        List<Object> list = new ArrayList<>();

        // Add all the elements to the list
        for (Ast.Expression expression : ast.getValues()) {
            list.add(visit(expression).getValue());
        }

        // Return the list
        return Environment.create(list);
    }

    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
