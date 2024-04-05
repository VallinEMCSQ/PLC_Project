package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
        throw new RuntimeException("Not Implemented");  // TODO
    }

    @Override
    public Void visit(Ast.Global ast) {
        throw new RuntimeException("Not Implemented");  // TODO
    }

    @Override
    public Void visit(Ast.Function ast) {
        throw new RuntimeException("Not Implemented");  // TODO
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        try {
            if (ast.getExpression().getClass() != Ast.Expression.Function.class) {
                throw new RuntimeException("Not function type!");
            }
        }
        catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        throw new RuntimeException("Not implemented!");
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
        if(ast.getThenStatements().isEmpty()){
            throw new RuntimeException("Then statements are empty!");
        }
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
        if(!(ast.getElseStatements().isEmpty())){
            for (int i = 0; i < ast.getElseStatements().size(); i++) {
                try {
                    scope = new Scope(scope);
                    visit(ast.getElseStatements().get(i));
                } finally {
                    scope = scope.getParent();
                }
            }
        }
        for (int i = 0; i < ast.getThenStatements().size(); i++) {
            try {
                scope = new Scope(scope);
                visit(ast.getThenStatements().get(i));
            } finally {
                scope = scope.getParent();
            }
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {
        throw new RuntimeException("Not Implemented");  // TODO
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {
        throw new RuntimeException("Not Implemented");  // TODO
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        throw new RuntimeException("Not Implemented");  // TODO
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        try {
            visit(ast.getValue());
            Environment.Variable variable = scope.lookupVariable("return");
            requireAssignable(variable.getType(), ast.getValue().getType());
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        try{
            if(ast.getLiteral() instanceof Boolean){
                ast.setType(Environment.Type.BOOLEAN);
                requireAssignable(Environment.Type.BOOLEAN, Environment.Type.BOOLEAN);
            }
            else if(ast.getLiteral() instanceof BigInteger){
                ast.setType(Environment.Type.INTEGER);
                requireAssignable(Environment.Type.INTEGER, Environment.Type.INTEGER);
            }
            else if(ast.getLiteral() instanceof BigDecimal){
                ast.setType(Environment.Type.DECIMAL);
                requireAssignable(Environment.Type.DECIMAL, Environment.Type.DECIMAL);
            }
            else if(ast.getLiteral() instanceof Character){
                ast.setType(Environment.Type.CHARACTER);
                requireAssignable(Environment.Type.CHARACTER, Environment.Type.CHARACTER);
            }
            else if(ast.getLiteral() instanceof String){
                ast.setType(Environment.Type.STRING);
                requireAssignable(Environment.Type.STRING, Environment.Type.STRING);
            }
            else{
                throw new RuntimeException("Not matching types!");

            }
        } catch (RuntimeException r) {
            throw new RuntimeException("Not matching types!");
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
            if (ast.getOperator().equals("+") || ast.getOperator().equals("-") || ast.getOperator().equals("*") || ast.getOperator().equals("/")) {
                if (ast.getLeft().getType() == Environment.Type.INTEGER && ast.getRight().getType() == Environment.Type.INTEGER) {
                    ast.setType(Environment.Type.INTEGER);
                    requireAssignable(Environment.Type.INTEGER, Environment.Type.INTEGER);
                } else if (ast.getLeft().getType() == Environment.Type.DECIMAL && ast.getRight().getType() == Environment.Type.DECIMAL) {
                    ast.setType(Environment.Type.DECIMAL);
                    requireAssignable(Environment.Type.DECIMAL, Environment.Type.DECIMAL);
                } else {
                    throw new RuntimeException("Not matching types!");
                }
            } else if (ast.getOperator().equals("==") || ast.getOperator().equals("!=") || ast.getOperator().equals("<") || ast.getOperator().equals("<=") || ast.getOperator().equals(">") || ast.getOperator().equals(">=")) {
                if (ast.getLeft().getType() == Environment.Type.INTEGER && ast.getRight().getType() == Environment.Type.INTEGER) {
                    ast.setType(Environment.Type.BOOLEAN);
                    requireAssignable(Environment.Type.BOOLEAN, Environment.Type.BOOLEAN);
                } else if (ast.getLeft().getType() == Environment.Type.DECIMAL && ast.getRight().getType() == Environment.Type.DECIMAL) {
                    ast.setType(Environment.Type.BOOLEAN);
                    requireAssignable(Environment.Type.BOOLEAN, Environment.Type.BOOLEAN);
                } else if (ast.getLeft().getType() == Environment.Type.CHARACTER && ast.getRight().getType() == Environment.Type.CHARACTER) {
                    ast.setType(Environment.Type.BOOLEAN);
                    requireAssignable(Environment.Type.BOOLEAN, Environment.Type.BOOLEAN);
                } else if (ast.getLeft().getType() == Environment.Type.STRING && ast.getRight().getType() == Environment.Type.STRING) {
                    ast.setType(Environment.Type.BOOLEAN);
                    requireAssignable(Environment.Type.BOOLEAN, Environment.Type.BOOLEAN);
                } else {
                    throw new RuntimeException("Not matching types!");
                }
            } else {
                throw new RuntimeException("Not matching types!");
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
            List<Environment.Type> parameterTypes = ast.getArguments().stream().map(Ast.Expression::getType).collect(Collectors.toList());
            Environment.Function function = scope.lookupFunction(ast.getName(), parameterTypes.size());
            ast.setFunction(function);
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        throw new RuntimeException("Not Implemented");  // TODO
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
