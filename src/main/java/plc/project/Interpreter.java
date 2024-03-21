package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
        // Visit all the global variables
        ast.getGlobals().forEach(this::visit);

        // Visit all the functions
        ast.getFunctions().forEach(this::visit);

        // Try to call the main/0 function
        try {
            // Lookup the main/0 function
            Environment.Function mainFunction = scope.lookupFunction("main", 0);

            // Prepare the arguments for the function
            List<Environment.PlcObject> arguments = new ArrayList<>();

            // Invoke the function and return its result
            Environment.PlcObject result = mainFunction.invoke(arguments);

            // Check if the result is 0
            if (result.getValue() instanceof BigInteger && ((BigInteger) result.getValue()).intValue() == 0) {
                return result;
            } else {
                throw new RuntimeException("The main/0 function does not return 0.");
            }
        } catch (Exception e) {
            // The main/0 function does not exist, throw an exception
            throw new RuntimeException("The main/0 function does not exist within source.");
        }
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
        scope.defineFunction(ast.getName(), ast.getParameters().size(), args -> {
            Scope oldScope = scope;
            scope = new Scope(oldScope);
            for (int i = 0; i < ast.getParameters().size(); i++) {
                scope.defineVariable(ast.getParameters().get(i), true, args.get(i));
            }
            try {
                for (Ast.Statement statement : ast.getStatements()) {
                    visit(statement);
                }
            } catch (Return returnValue) {
                return returnValue.value;
            } finally {
                scope = oldScope;
            }
            return Environment.NIL;
        });
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Expression ast) {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Declaration ast) {
        Optional optional = ast.getValue();
        Optional<Ast.Expression> op1 = ast.getValue();
        Boolean present = optional.isPresent();

        if (present){
            Object object = optional.get();
            System.out.println(object.toString());

            Ast.Expression expr = (Ast.Expression) optional.get();
            Ast.Expression expr1 = op1.get();

            scope.defineVariable(ast.getName(), true, visit(expr));
        } else {
            scope.defineVariable(ast.getName(), true, Environment.NIL);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Assignment ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Switch ast) {
        throw new UnsupportedOperationException(); //TODO
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
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Binary ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Access ast) {
        // Return the value of the appropriate variable in the current scope. For a list, evaluate the offset and return the value of the appropriate offset.
        Environment.Variable variable = scope.lookupVariable(ast.getName());
        if (ast.getOffset().isPresent()){
            int offset = requireType(Integer.class, visit(ast.getOffset().get()));
            return Environment.create(requireType(List.class, variable.getValue()).get(offset));
        } else {
            return variable.getValue();
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Function ast) {
        throw new UnsupportedOperationException(); //TODO
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
