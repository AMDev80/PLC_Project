package plc.project;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.io.PrintWriter;
import java.io.StringWriter;


public final class Generator implements Ast.Visitor<Void> {

    private PrintWriter writer;
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
        indent++;

        // fields
        if (!ast.getFields().isEmpty()) {
            newline(indent);
            for (int i = 0; i < ast.getFields().size(); i++) {
                visit(ast.getFields().get(i));
                newline(indent);
            }
        }

        // Always print a blank line after fields (even if no fields)
        newline(0);

        // Print main method header
        // According to the expected: One blank line after class,
        // then indent the main method at indent=1:
        newline(indent);
        print("public static void main(String[] args) {");
        indent++;
        newline(indent);
        print("System.exit(new Main().main());");
        indent--;
        newline(indent);
        print("}");

        // Another blank line after main method
        newline(0);

        // Other methods
        for (int i = 0; i < ast.getMethods().size(); i++) {
            newline(indent);
            visit(ast.getMethods().get(i));
            newline(0);
        }

        indent--;
        print("}");
        return null;
    }




    @Override
    public Void visit(Ast.Field ast) {
        // type and jvmName from ast.getVariable()
        Environment.Variable var = ast.getVariable();
        // var.getType().getJvmName() for the type
        // var.getName() for the jvm name (it's the same as variable name)
        if (ast.getConstant()) {
            print("final ");
        }
        print(var.getType().getJvmName(), " ", var.getJvmName());
        if (ast.getValue().isPresent()) {
            print(" = ");
            visit(ast.getValue().get());
        }
        print(";");
        return null;
    }


    @Override
    public Void visit(Ast.Method ast) {
        Environment.Function function = ast.getFunction();
        String returnType = function.getReturnType().getJvmName();
        // If NIL => void
        if (function.getReturnType().equals(Environment.Type.NIL)) {
            returnType = "void";
        }

        print(returnType, " ", function.getJvmName(), "(");
        // parameters
        for (int i = 0; i < function.getParameterTypes().size(); i++) {
            if (i > 0) print(", ");
            Environment.Type paramType = function.getParameterTypes().get(i);
            String paramName = ast.getParameters().get(i);
            print(paramType.getJvmName(), " ", paramName);
        }
        print(") ");

        // body
        if (ast.getStatements().isEmpty()) {
            print("{}");
        } else {
            print("{");
            indent++;
            for (Ast.Statement stmt : ast.getStatements()) {
                newline(indent);
                visit(stmt);
            }
            indent--;
            newline(indent);
            print("}");
        }

        return null;
    }


    @Override
    public Void visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        print(";");
        return null;
    }


    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        Environment.Variable var = ast.getVariable();
        print(var.getType().getJvmName(), " ", var.getJvmName());
        if (ast.getValue().isPresent()) {
            print(" = ");
            visit(ast.getValue().get());
        }
        print(";");
        return null;
    }


    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        // receiver must be access
        Ast.Expression.Access access = (Ast.Expression.Access) ast.getReceiver();
        print(access.getVariable().getJvmName(), " = ");
        visit(ast.getValue());
        print(";");
        return null;
    }


    @Override
    public Void visit(Ast.Statement.If ast) {
        print("if (");
        visit(ast.getCondition());
        print(") ");
        if (ast.getThenStatements().isEmpty()) {
            print("{}");
        } else {
            print("{");
            indent++;
            for (Ast.Statement stmt : ast.getThenStatements()) {
                newline(indent);
                visit(stmt);
            }
            indent--;
            newline(indent);
            print("}");
        }

        if (!ast.getElseStatements().isEmpty()) {
            print(" else ");
            if (ast.getElseStatements().isEmpty()) {
                print("{}");
            } else {
                print("{");
                indent++;
                for (Ast.Statement stmt : ast.getElseStatements()) {
                    newline(indent);
                    visit(stmt);
                }
                indent--;
                newline(indent);
                print("}");
            }
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.For ast) {
        print("for (");
        if (ast.getInitialization() != null) {
            print(" ");
            print(generateInlineStatement(ast.getInitialization()));
        }
        print(";");

        if (ast.getCondition() != null) {
            print(" ");
            visit(ast.getCondition());
        }
        print(";");

        if (ast.getIncrement() != null) {
            print(" ");
            print(generateInlineStatement(ast.getIncrement()));
        }
        print(" ) ");

        if (ast.getStatements().isEmpty()) {
            print("{}");
        } else {
            print("{");
            indent++;
            for (Ast.Statement stmt : ast.getStatements()) {
                newline(indent);
                visit(stmt);
            }
            indent--;
            newline(indent);
            print("}");
        }
        return null;
    }


    private String generateInlineStatement(Ast.Statement stmt) {
        StringWriter sw = new StringWriter();
        PrintWriter tempWriter = new PrintWriter(sw);
        PrintWriter originalWriter = this.writer;

        try {
            this.writer = tempWriter;
            visit(stmt); // Generate the statement
        } catch (Exception e) {
            throw new RuntimeException("Error generating inline statement for: " + stmt.getClass().getSimpleName(), e);
        } finally {
            this.writer = originalWriter; // Restore the original writer
        }

        tempWriter.flush();
        String result = sw.toString().trim();

        // Remove the trailing semicolon if present
        if (result.endsWith(";")) {
            result = result.substring(0, result.length() - 1).trim();
        }

        return result;
    }




    @Override
    public Void visit(Ast.Statement.While ast) {
        print("while (");
        visit(ast.getCondition());
        print(") ");
        if (ast.getStatements().isEmpty()) {
            print("{}");
        } else {
            print("{");
            indent++;
            for (Ast.Statement stmt : ast.getStatements()) {
                newline(indent);
                visit(stmt);
            }
            indent--;
            newline(indent);
            print("}");
        }
        return null;
    }


    @Override
    public Void visit(Ast.Statement.Return ast) {
        print("return ");
        visit(ast.getValue());
        print(";");
        return null;
    }


    @Override
    public Void visit(Ast.Expression.Literal ast) {
        Object value = ast.getLiteral();
        if (value == null) {
            print("null");
        } else if (value instanceof Boolean) {
            print(((Boolean)value) ? "true" : "false");
        } else if (value instanceof Character) {
            print("'", value.toString(), "'");
        } else if (value instanceof String) {
            print("\"", value.toString(), "\"");
        } else if (value instanceof BigInteger) {
            print(value.toString());
        } else if (value instanceof BigDecimal) {
            print(value.toString());
        } else {
            throw new RuntimeException("Unknown literal type");
        }
        return null;
    }


    @Override
    public Void visit(Ast.Expression.Group ast) {
        print("(");
        visit(ast.getExpression());
        print(")");
        return null;
    }


    @Override
    public Void visit(Ast.Expression.Binary ast) {
        visit(ast.getLeft());
        print(" ", ast.getOperator(), " ");
        visit(ast.getRight());
        return null;
    }


    @Override
    public Void visit(Ast.Expression.Access ast) {
        // If receiver present: receiver.name => print as (receiver).name?
        // Actually from specs: field access expression is represented as nested Access: The variable in Access node has jvmName
        // If has receiver:
        if (ast.getReceiver().isPresent()) {
            visit(ast.getReceiver().get());
            print(".", ast.getVariable().getJvmName());
        } else {
            print(ast.getVariable().getJvmName());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        if (ast.getReceiver().isPresent()) {
            visit(ast.getReceiver().get());
            print(".", ast.getFunction().getJvmName());
        } else {
            print(ast.getFunction().getJvmName());
        }

        print("(");
        for (int i = 0; i < ast.getArguments().size(); i++) {
            if (i > 0) print(", ");
            visit(ast.getArguments().get(i));
        }
        print(")");

        return null;
    }

}
