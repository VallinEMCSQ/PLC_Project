package plc.project;

import java.io.PrintWriter;

import java.util.Optional;
import java.util.List;
public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        print("public class Main {");
        newline(0);

        // Visit globals
        for (Ast.Global global : ast.getGlobals()) {
            visit(global);
        }

        newline(1);
        print("public static void main(String[] args) {");
        newline(2);
        print("System.exit(new Main().main());");
        newline(1);
        print("}");
        newline(0);

        // Visit functions
        for (Ast.Function function : ast.getFunctions()) {
            newline(0);
            visit(function);
        }

        newline(0);
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Global ast) {
        if (ast.getValue().isPresent() && ast.getValue().get() instanceof Ast.Expression.PlcList) {
            Ast.Expression.PlcList listExpr = (Ast.Expression.PlcList) ast.getValue().get();
            if (listExpr.getValues().isEmpty()) {
                print("double[] ", ast.getName(), " = {};");
            } else {
                print("double[] ", ast.getName(), " = {");
                for (int i = 0; i < listExpr.getValues().size(); i++) {
                    if (i > 0) {
                        print(", ");
                    }
                    visit(listExpr.getValues().get(i));
                }
                print("};");
            }
        } else {
            print(ast.getTypeName(), " ", ast.getName());
            if (ast.getValue().isPresent()) {
                print(" = ", ast.getValue().get());
            }
            print(";");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Function ast) {
        String jvmName = ast.getFunction().getReturnType().getJvmName();

        print("    ", jvmName, " ", ast.getName(), "(");
        List<String> parameters = ast.getParameters();
        List<String> parameterTypeNames = ast.getParameterTypeNames();
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                print(", ");
            }
            String parameterTypeName = parameterTypeNames.get(i);
            String parameter = parameters.get(i);
            print(parameterTypeName, " ", parameter);
        }
        print(") {");
        newline(1);

        List<Ast.Statement> statements = ast.getStatements();
        for (Ast.Statement statement : statements) {
            visit(statement);
        }

        print("    }");
        newline(0);
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        print("    ");
        if (ast.getExpression() instanceof Ast.Expression.Function) {
            Ast.Expression.Function function = (Ast.Expression.Function) ast.getExpression();
            if (function.getName().equals("print")) {
                print("System.out.println(", function.getArguments().get(0), ");");
            } else {
                print(ast.getExpression(), ";");
            }
        } else {
            print(ast.getExpression(), ";");
        }
        newline(0);
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        String typeName = ast.getTypeName().orElse("var");
        if (typeName.equals("var")) {
            print("double ");
        } else {
            String jvmName = Environment.getType(typeName).getJvmName();
            print(jvmName, " ");
        }

        print(ast.getName());

        ast.getValue().ifPresent(value -> {
            print(" = ");
            visit(value);
        });

        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        print("        ", ast.getReceiver(), " = ", ast.getValue(), ";");
        newline(0);
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        print("if (", ast.getCondition(), ") {");
        newline(0);
        for (Ast.Statement statement : ast.getThenStatements()) {
            visit(statement);
        }
        print("}");
        //newline(0);
        if (!ast.getElseStatements().isEmpty()) {
            print(" else {");
            newline(0);
            for (Ast.Statement statement : ast.getElseStatements()) {
                visit(statement);
            }
            print("}");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {
        print("switch (", ast.getCondition(), ") {");
        newline(1);
        for (Ast.Statement.Case caseStmt : ast.getCases()) {
            visit(caseStmt);
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {
        if (ast.getValue().isPresent()) {
            print("case ", ast.getValue().get(), ":");
        } else {
            print("    default:");
        }
        newline(1);
        for (Ast.Statement statement : ast.getStatements()) {
            visit(statement);
        }
        if (ast.getValue().isPresent()) {
            print("        break;");
            newline(0);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        print("while (", ast.getCondition(), ") {");
        newline(1);
        for (Ast.Statement statement : ast.getStatements()) {
            visit(statement);
        }
        print("        }");
        newline(0);
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        Ast.Expression value = ast.getValue();
        print("        return ");
        if (value instanceof Ast.Expression.Literal) {
            print(((Ast.Expression.Literal) value).getLiteral().toString());
        } else if (value instanceof Ast.Expression.Group) {
            print("(", ((Ast.Expression.Group) value).getExpression(), ")");
        } else if (value instanceof Ast.Expression.Binary) {
            Ast.Expression.Binary binary = (Ast.Expression.Binary) value;
            print(binary.getLeft(), " ", binary.getOperator(), " ", binary.getRight());
        } else if (value instanceof Ast.Expression.Access) {
            Ast.Expression.Access access = (Ast.Expression.Access) value;
            if (access.getOffset().isPresent()) {
                print(access.getName(), "[", access.getOffset().get(), "]");
            } else {
                print(access.getName());
            }
        } else if (value instanceof Ast.Expression.Function) {
            Ast.Expression.Function function = (Ast.Expression.Function) value;
            print(function.getName(), "(", String.join(", ", function.getArguments().toString()), ")");
        } else if (value instanceof Ast.Expression.PlcList) {
            Ast.Expression.PlcList plcList = (Ast.Expression.PlcList) value;
            print("[", String.join(", ", plcList.getValues().toString()), "]");
        } else {
            print("null");
        }
        print(";");
        newline(0);
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        Object literal = ast.getLiteral();
        if (literal instanceof String) {
            print("\"" + literal + "\"");
        } else if (literal instanceof Character) {
            print("'" + literal + "'");
        } else {
            print(literal);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        print("(", ast.getExpression(), ")");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        if (ast.getOperator().equals("+")) {
            visit(ast.getLeft());
            print(" + ");
            visit(ast.getRight());
        } else if (ast.getOperator().equals("&&")) {
            visit(ast.getLeft());
            print(" && ");
            visit(ast.getRight());
        } else {
            print("(");
            visit(ast.getLeft());
            print(" ", ast.getOperator(), " ");
            visit(ast.getRight());
            print(")");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        if (ast.getOffset().isPresent()) {
            print(ast.getName(), "[", ast.getOffset().get(), "]");
        } else {
            print(ast.getName());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        String jvmName = ast.getFunction().getJvmName();

        print(jvmName, "(");

        List<Ast.Expression> arguments = ast.getArguments();
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) {
                print(", ");
            }
            visit(arguments.get(i));
        }

        print(")");

        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        print("[");
        List<Ast.Expression> values = ast.getValues();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                print(", ");
            }
            visit(values.get(i));
        }
        print("]");
        return null;
    }

}
