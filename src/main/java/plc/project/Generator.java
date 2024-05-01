package plc.project;

import java.io.PrintWriter;

import java.math.BigDecimal;
import java.math.BigInteger;
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
        this.indent++;

        // Visit globals
        if (!ast.getGlobals().isEmpty()) {
            for (int i = 0; i < ast.getGlobals().size(); i++) {
                newline(this.indent);
                print(ast.getGlobals().get(i));
            }
            newline(0);
        }

        newline(this.indent);
        print("public static void main(String[] args) {");
        newline(this.indent + 1);
        print("System.exit(new Main().main());");
        newline(this.indent);
        print("}");
        newline(0);

        // Visit functions
        if (!ast.getFunctions().isEmpty()) {
            for (int i = 0; i < ast.getFunctions().size(); i++) {
                newline(this.indent);
                print(ast.getFunctions().get(i));
                newline(0);
            }
        }
        this.indent--;
        newline(this.indent);
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Global ast) {
        if (!ast.getMutable()){
            print("final ");
        }
        print(ast.getVariable().getType().getJvmName());
        if (ast.getValue().isPresent()){
            if (ast.getValue().get().getClass() == Ast.Expression.PlcList.class){
                print("[] ", ast.getVariable().getJvmName(), " = ");
                visit(ast.getValue().get());
            }
            else{
                print(" ", ast.getVariable().getJvmName(), " = ", ast.getValue().get());
            }
        }
        else{
            print(" ", ast.getVariable().getJvmName());
        }
        print(";");
        return null;
        /*if (ast.getValue().isPresent() && ast.getValue().get() instanceof Ast.Expression.PlcList) {
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

        return null;*/
    }

    @Override
    public Void visit(Ast.Function ast) {
        print(ast.getFunction().getReturnType().getJvmName(), " ");
        print(ast.getFunction().getJvmName(), "(");
        for(int i = 0; i < ast.getFunction().getArity(); i++){
            print(ast.getFunction().getParameterTypes().get(i).getJvmName(), " ");
            print(ast.getParameters().get(i));
            if(i<ast.getFunction().getArity() - 1){
                print(", ");
            }
        }
        print(") {");
        if(!ast.getStatements().isEmpty()){
            this.indent++;
            for (int i = 0; i < ast.getStatements().size(); i++) {
                newline(this.indent);
                print(ast.getStatements().get(i));
            }
            this.indent--;
            newline(this.indent);
        }
        print("}");
        return null;
        /*String jvmName = ast.getFunction().getReturnType().getJvmName();

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
        return null;*/
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        print(ast.getExpression(), ";");
        return null;
        /*print("    ");
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
        return null;*/
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        print(ast.getVariable().getType().getJvmName(), " ");
        print(ast.getVariable().getJvmName());
        if (ast.getValue().isPresent()){
            print(" = ", ast.getValue().get());
        }
        print(";");
        return null;
        /*String typeName = ast.getTypeName().orElse("var");
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
        return null;*/
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        print(ast.getReceiver(), " = ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        print("if (", ast.getCondition(), ") {");
        this.indent++;
        for (int i = 0; i < ast.getThenStatements().size(); i++) {
            newline(this.indent);
            print(ast.getThenStatements().get(i));
        }
        this.indent--;
        newline(this.indent);
        print("}");
        if(!ast.getElseStatements().isEmpty()){
            print (" else {");
            this.indent++;
            for (int i = 0; i < ast.getElseStatements().size(); i++) {
                newline(this.indent);
                print(ast.getElseStatements().get(i));
            }
            this.indent--;
            newline(this.indent);
            print("}");
        }
        return null;
        /*print("if (", ast.getCondition(), ") {");
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
        return null;*/
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {
        print("switch (", ast.getCondition(), ") {");
        for (int i = 0; i < ast.getCases().size(); i++) {
            print(ast.getCases().get(i));
        }
        newline(this.indent);
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {
        this.indent++;
        newline(this.indent);
        if(ast.getValue().isPresent()){
            print("case ", ast.getValue().get(), ":");
            this.indent++;
            for (int i = 0; i < ast.getStatements().size(); i++) {
                newline(this.indent);
                print(ast.getStatements().get(i));
            }
            newline(this.indent);
            print("break;");
        }
        else{
            print("default:");
            this.indent++;
            for(int i = 0; i < ast.getStatements().size(); i++){
                newline(this.indent);
                print(ast.getStatements().get(i));

            }
        }
        this.indent -= 2;
        return null;
        /*if (ast.getValue().isPresent()) {
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
        return null;*/
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        print("while (", ast.getCondition(), ") {");
        if (!ast.getStatements().isEmpty()) {
            this.indent++;
            for (int i = 0; i < ast.getStatements().size(); i++) {
                newline(this.indent);
                print(ast.getStatements().get(i));
            }
            this.indent--;
            newline(this.indent);
        }
        print("}");
        return null;
        /*print("while (", ast.getCondition(), ") {");
        newline(1);
        for (Ast.Statement statement : ast.getStatements()) {
            visit(statement);
        }
        print("        }");
        newline(0);
        return null;*/
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        print("return ", ast.getValue(), ";");
        return null;
        /*Ast.Expression value = ast.getValue();
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
        return null;*/
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        if (ast.getType() == Environment.Type.BOOLEAN) {
            print((Boolean)ast.getLiteral());
        } else if (ast.getType() == Environment.Type.INTEGER) {
            BigInteger integer = (BigInteger)ast.getLiteral();
            print(integer.intValueExact());
        } else if (ast.getType() == Environment.Type.DECIMAL) {
            BigDecimal decimal = (BigDecimal)ast.getLiteral();
            print(decimal.doubleValue());
        } else if (ast.getType() == Environment.Type.CHARACTER) {
            print("'", (char)ast.getLiteral(), "'");
        } else if (ast.getType() == Environment.Type.STRING) {
            print("\"", (String)ast.getLiteral(), "\"");
        } else if (ast.getType() == Environment.Type.NIL) {
            print("null");
        }
        return null;
        /*Object literal = ast.getLiteral();
        if (literal instanceof String) {
            print("\"" + literal + "\"");
        } else if (literal instanceof Character) {
            print("'" + literal + "'");
        } else {
            print(literal);
        }
        return null;*/
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        print("(", ast.getExpression(), ")");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        if(ast.getOperator().equals("^")){
            print("Math.pow(", ast.getLeft(), ", ", ast.getRight(), ")");
        }
        else{
            print(ast.getLeft(), " ", ast.getOperator(), " ", ast.getRight());

        }
        return null;
        /*if (ast.getOperator().equals("+")) {
            visit(ast.getLeft());
            print(" + ");
            visit(ast.getRight());
        } else if (ast.getOperator().equals("-")) {
            visit(ast.getLeft());
            print(" - ");
            visit(ast.getRight());
        } else if (ast.getOperator().equals("*")) {
            visit(ast.getLeft());
            print(" * ");
            visit(ast.getRight());
        } else if (ast.getOperator().equals("/")) {
            visit(ast.getLeft());
            print(" / ");
            visit(ast.getRight());
        } else if (ast.getOperator().equals("%")) {
            visit(ast.getLeft());
            print(" % ");
            visit(ast.getRight());
        } else if (ast.getOperator().equals("<")) {
            visit(ast.getLeft());
            print(" < ");
            visit(ast.getRight());
        } else if (ast.getOperator().equals("<=")) {
            visit(ast.getLeft());
            print(" <= ");
            visit(ast.getRight());
        } else if (ast.getOperator().equals(">")) {
            visit(ast.getLeft());
            print(" > ");
            visit(ast.getRight());
        } else if (ast.getOperator().equals(">=")) {
            visit(ast.getLeft());
            print(" >= ");
            visit(ast.getRight());
        } else if (ast.getOperator().equals("==")) {
            visit(ast.getLeft());
            print(" == ");
            visit(ast.getRight());
        } else if (ast.getOperator().equals("!=")) {
            visit(ast.getLeft());
            print(" != ");
            visit(ast.getRight());
        } else if (ast.getOperator().equals("&&")) {
            visit(ast.getLeft());
            print(" && ");
            visit(ast.getRight());
        } else if (ast.getOperator().equals("||")) {
            visit(ast.getLeft());
            print(" ", ast.getOperator(), " ");
            visit(ast.getRight());
        } else if (ast.getOperator().equals("^")) {
            print("Math.pow(", ast.getLeft(), ", ", ast.getRight(), ")");
        }
        else {
            throw new RuntimeException("Invalid operator: " + ast.getOperator());
        }

        return null;*/
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

        for(int i = 0; i < ast.getArguments().size(); i++){
            if (i > 0) {
                print(", ");
            }
            print(ast.getArguments().get(i));
        }
        /*List<Ast.Expression> arguments = ast.getArguments();
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) {
                print(", ");
            }
            visit(arguments.get(i));
        }*/
        print(")");

        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        print("{");
        //List<Ast.Expression> values = ast.getValues();
        for (int i = 0; i < ast.getValues().size(); i++) {
            if (i > 0) {
                print(", ");
            }
            print(ast.getValues().get(i));
        }
        print("}");
        return null;
    }

}
