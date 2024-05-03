package plc.project;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class EndToEndGeneratorTests {

    /*
    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testFunction(String test, String input, String expected) {
        test(input, expected, Parser::parseFunction);
    }

    private static Stream<Arguments> testFunction() {
        return Stream.of(
                Arguments.of("Square",
                        // FUN square(num: Decimal): Decimal DO
                        //     RETURN num * num;
                        // END
                        "FUN square(num: Decimal): Decimal DO\n    RETURN num * num;\nEND",
                        String.join(System.lineSeparator(),
                                "double square(double num) {",
                                "    return num * num;",
                                "}"
                        )
                ),
                Arguments.of("Function Invalid Argument",
                        // FUN square(num: Decimal): Decimal DO
                        //     RETURN num * num;
                        // END
                        "FUN square(num: Integer): Decimal DO\n    RETURN num * num;\nEND",
                        String.join(System.lineSeparator(),
                                "double square(int num) {",
                                "    return num * num;",
                                "}"
                        )
                )
        );
    }


     */
    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testGlobal(String test, String input, String expected) {
        test(input, expected, Parser::parseGlobal);
    }

    private static Stream<Arguments> testGlobal() {
        return Stream.of(
                Arguments.of("Mutable Declaration",
                        // VAR name: Integer;
                        "VAR name: Integer;",
                        "int name;"
                ),
                Arguments.of("List Declaration",
                        // LIST nums: Integer = [1, 2, 3];
                        "LIST nums: Integer = [1, 2, 3];",
                        "int[] nums = {1, 2, 3};"
                )
        );
    }


    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testSource(String test, String input, String expected) {
        test(input, expected, Parser::parseSource);
    }

    private static Stream<Arguments> testSource() {
        return Stream.of(
                Arguments.of("Hello, World!",
                        // FUN main(): Integer DO
                        //     print("Hello, World!");
                        //     RETURN 0;
                        // END
                        "FUN main(): Integer DO\n    print(\"Hello, World!\");\n    RETURN 0;\nEND",
                        String.join(System.lineSeparator(),
                                "public class Main {",
                                "",
                                "    public static void main(String[] args) {",
                                "        System.exit(new Main().main());",
                                "    }",
                                "",
                                "    int main() {",
                                "        System.out.println(\"Hello, World!\");",
                                "        return 0;",
                                "    }",
                                "",
                                "}"
                        )
                )/*,
                Arguments.of("List Initialization",
                        // FUN main(): Integer DO
                        //     LIST list: Decimal = [1.0, 1.5, 2.0];
                        //     RETURN 0;
                        // END
                        "LIST list: Decimal = [1.0, 1.5, 2.0];\n    FUN main(): Integer DO\n    list[0] = 3.0;\n    RETURN 0;\nEND",
                        String.join(System.lineSeparator(),
                                "public class Main {",
                                "",
                                "    double[] list = {1.0, 1.5, 2.0};",
                                "",
                                "    public static void main(String[] args) {",
                                "        System.exit(new Main().main());",
                                "    }",
                                "",
                                "    int main() {",
                                "        list[0] = 3.0;",
                                "        return 0;",
                                "    }",
                                "",
                                "}"
                        )
                ),
                Arguments.of("Global List",
                        // LIST list: Integer = [1, 2, 3];
                        // FUN main(): Integer DO
                        //     print(list[1]);
                        // END
                        "LIST list: Integer = [1, 2, 3];\nFUN main(): Integer DO\n    print(list[1]);\nEND",
                        String.join(System.lineSeparator(),
                                "public class Main {",
                                "",
                                "    int[] list = {1, 2, 3};",
                                "",
                                "    public static void main(String[] args) {",
                                "        System.exit(new Main().main());",
                                "    }",
                                "",
                                "    int main() {",
                                "        System.out.println(list[1]);",
                                "        return 0;",
                                "    }",
                                "",
                                "}"
                        )
                )*/
        );
    }

    @Test
    void testList() {
        // LIST list: Decimal = [1.0, 1.5, 2.0];
        String input = new String("LIST list: Decimal = [1.0, 1.5, 2.0];");
        String expected = new String("double[] list = {1.0, 1.5, 2.0};");
        test(input, expected, Parser::parseGlobal);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testDeclarationStatement(String test, String input, String expected) {
        test(input, expected, Parser::parseStatement);
    }

    private static Stream<Arguments> testDeclarationStatement() {
        return Stream.of(
                Arguments.of("Declaration",
                        // LET name: Integer;
                        "LET name: Integer;",
                        "int name;"
                ),
                Arguments.of("Initialization",
                        // LET name = 1.0;
                        "LET name = 1.0;",
                        "double name = 1.0;"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testIfStatement(String test, String input, String expected) {
        test(input, expected, Parser::parseStatement);
    }

    private static Stream<Arguments> testIfStatement() {
        return Stream.of(
                Arguments.of("If",
                        // IF TRUE DO
                        //     print(1);
                        // END
                        "IF TRUE DO\n    print(1);\nEND",
                        String.join(System.lineSeparator(),
                                "if (true) {",
                                "    System.out.println(1);",
                                "}"
                        )
                ),
                Arguments.of("Else",
                        // IF FALSE DO
                        //     print(1);
                        // ELSE
                        //     print(0);
                        // END
                        "IF FALSE DO\n    print(1);\nELSE\n    print(0);\nEND",
                        String.join(System.lineSeparator(),
                                "if (false) {",
                                "    System.out.println(1);",
                                "} else {",
                                "    System.out.println(0);",
                                "}"
                        )
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testSwitchStatement(String test, String input, String expected) {
        test(input, expected, Parser::parseStatement);
    }

    private static Stream<Arguments> testSwitchStatement() {
        return Stream.of(
                Arguments.of("Switch",
                        // SWITCH 'y'
                        //     CASE 'y':
                        //         print("yes");
                        //     DEFAULT
                        //         print("no");
                        // END
                        "SWITCH 'y'\n    CASE 'y':\n        print(\"yes\");\n    DEFAULT\n        print(\"no\");\nEND",
                        String.join(System.lineSeparator(),
                                "switch ('y') {",
                                "    case 'y':",
                                "        System.out.println(\"yes\");",
                                "        break;",
                                "    default:",
                                "        System.out.println(\"no\");",
                                "}"
                        )
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testBinaryExpression(String test, String input, String expected) {
        test(input, expected, Parser::parseExpression);
    }

    private static Stream<Arguments> testBinaryExpression() {
        return Stream.of(
                Arguments.of("And",
                        // TRUE && FALSE
                        "TRUE && FALSE",
                        "true && false"
                ),
                Arguments.of("Concatenation",
                        // "Ben" + 10
                        "\"Ben\" + 10",
                        "\"Ben\" + 10"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testFunctionExpression(String test, String input, String expected) {
        test(input, expected, Parser::parseExpression);
    }

    private static Stream<Arguments> testFunctionExpression() {
        return Stream.of(
                Arguments.of("Print",
                        // print("Hello, World!")
                        "print(\"Hello, World!\")",
                        "System.out.println(\"Hello, World!\")"
                )
        );
    }

    @Test
    void testGlobalListInitialization() {
        // LIST list: Decimal = [1, 1.5, 2]; // Attempt to initialize with mixed types
        String input = "LIST list: Decimal = [1, 1.5, 2];";
        String expected = "Unexpected java.lang.RuntimeException: java.lang.RuntimeException: Not matching types!";
        test(input, expected, Parser::parseGlobal);
    }

    @Test
    void testFunctionSquare() {
        // FUN square(x: Integer): Integer DO RETURN x * x; END // Incorrect type of the return statement
        String input = "FUN square(x: Integer): Integer DO RETURN x * x; END";
        String expected = "Unexpected java.lang.RuntimeException: java.lang.RuntimeException: java.lang.RuntimeException: Not matching types!";
        test(input, expected, Parser::parseFunction);
    }

    @Test
    void testFunctionMultipleStatements() {
        // FUN testFunction(): Integer DO x = 1; RETURN x; END // Returning a variable defined in a different scope
        String input = "FUN testFunction(): Integer DO x = 1; RETURN x; END";
        String expected = "Unexpected java.lang.RuntimeException: Error in visit method of Ast.Statement.Expression: java.lang.RuntimeException: java.lang.RuntimeException: The variable x is not defined in this scope.";
        test(input, expected, Parser::parseFunction);
    }

    @Test
    void testExpressionAccessList() {
        // list[1] // Attempt to access uninitialized list
        String input = "list[1];";
        String expected = "Unexpected java.lang.IllegalStateException (type is uninitialized)";
        test(input, expected, Parser::parseExpression);
    }

    /**
     * Helper function for tests, using a StringWriter as the output stream.
     */
    private static <T extends Ast> void test(String input, String expected, Function<Parser, T> function) {
        StringWriter writer = new StringWriter();
        Lexer lexer = new Lexer(input);
        Parser parser = new Parser(lexer.lex());

        Ast ast = function.apply(parser);

        Analyzer analyzer = new Analyzer(new Scope(null));
        analyzer.visit(ast);
        new Generator(new PrintWriter(writer)).visit(ast);
        Assertions.assertEquals(expected, writer.toString());
    }

    /**
     * Runs a callback on the given value, used for inline initialization.
     */
    private static <T> T init(T value, Consumer<T> initializer) {
        initializer.accept(value);
        return value;
    }
}
