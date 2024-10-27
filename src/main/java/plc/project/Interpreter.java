package plc.project;

import java.util.ArrayList;
import java.util.List;
import java.math.BigInteger;
import java.math.BigDecimal;

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
        // visit fields
        for (Ast.Field field : ast.getFields()) {
            visit(field);
        }

        // visit methods
        for (Ast.Method method : ast.getMethods()) {
            visit(method);
        }

        // Look up the main with arity 0
        Environment.Function main_func;
        try {
            main_func = scope.lookupFunction("main", 0);
        }
        catch (RuntimeException e) {
            throw new RuntimeException("Main  not defined.");
        }

        // call main
        return main_func.invoke(new ArrayList<>());
    }

    @Override
    public Environment.PlcObject visit(Ast.Field ast) {
        // visit initial ast value
        Environment.PlcObject value = Environment.NIL;
        if (ast.getValue().isPresent()) {
            value = visit(ast.getValue().get());
        }

        // define in scope
        scope.defineVariable(ast.getName(), ast.getConstant(), value);

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Method ast) {
        // get scope
        Scope current_scope = this.scope;
        // Define the func in scope
        scope.defineFunction(ast.getName(), ast.getParameters().size(), args -> {
            // new scope for function
            Scope prev_scope = this.scope;
            this.scope = new Scope(current_scope);
            try {
                // args to parms
                for (int i = 0; i < ast.getParameters().size(); i++) {
                    String params = ast.getParameters().get(i);
                    Environment.PlcObject arg_value = args.get(i);
                    this.scope.defineVariable(params, false, arg_value);
                }
                // visit statements
                for (Ast.Statement stmt : ast.getStatements()) {
                    visit(stmt);
                }
                // return NIL if there is no return statement
                return Environment.NIL;
            }
            catch (Return return_ex) {
                return return_ex.value;
            }
            finally {
                // Restore scope
                this.scope = prev_scope;
            }
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
        // visit present value
        Environment.PlcObject value = Environment.NIL;
        if (ast.getValue().isPresent()) {
            value = visit(ast.getValue().get());
        }

        // define variable within the current scope
        scope.defineVariable(ast.getName(), false, value);

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Assignment ast) {
        // Exception handling for receiver if it's not an Access expression
        if (!(ast.getReceiver() instanceof Ast.Expression.Access)) {
            throw new RuntimeException("Target for assignment is invalid.");
        }

        Ast.Expression.Access access = (Ast.Expression.Access) ast.getReceiver();

        // visit the value to assign
        Environment.PlcObject value = visit(ast.getValue());

        if (access.getReceiver().isPresent()) {
            // visit for receiver object
            Environment.PlcObject rec_obj = visit(access.getReceiver().get());

            // Set field for receiver object
            rec_obj.setField(access.getName(), value);
        }
        else {
            // Get variable from scope
            Environment.Variable variable = scope.lookupVariable(access.getName());

            // Check initialization and if its a constant
            if (variable.getConstant() && variable.getValue() != Environment.NIL) {
                throw new RuntimeException("No assigning to constants.");
            }

            // Set value
            variable.setValue(value);
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {
        // visit condition
        Environment.PlcObject condition_val = visit(ast.getCondition());
        Boolean condition = requireType(Boolean.class, condition_val);

        // Create a new scope
        Scope prev_scope = this.scope;
        this.scope = new Scope(prev_scope);

        try {
            if (condition) {
                // visit then statements
                for (Ast.Statement stmt : ast.getThenStatements()) {
                    visit(stmt);
                }
            }
            else {
                // visit else statements
                for (Ast.Statement stmt : ast.getElseStatements()) {
                    visit(stmt);
                }
            }
        }
        finally {
            // Restore scope
            this.scope = prev_scope;
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.For ast) {
        // Create loop scope
        Scope prev_scope = this.scope;
        this.scope = new Scope(prev_scope);

        try {
            // visit the initialization
            if (ast.getInitialization() != null) {
                visit(ast.getInitialization());
            }
            while (true) {
                // visit condition
                Environment.PlcObject condition_val = visit(ast.getCondition());
                Boolean condition = requireType(Boolean.class, condition_val);
                if (!condition) {
                    break; // no condition end loop
                }

                // new scope for loop body
                Scope body_scope = new Scope(this.scope);
                Scope prev_body_scope = this.scope;
                this.scope = body_scope;
                try {
                    // visit body statements
                    for (Ast.Statement stmt : ast.getStatements()) {
                        visit(stmt);
                    }
                }
                finally {
                    // Restore loop body scope
                    this.scope = prev_body_scope;
                }

                // visit the increment if present
                if (ast.getIncrement() != null) {
                    visit(ast.getIncrement());
                }
            }
        }
        finally {
            // Restore scope
            this.scope = prev_scope;
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.While ast) {
        // much like the ast.statement.for, but simpler
        // create a new scope for the while loop
        Scope prev_scope = this.scope;
        this.scope = new Scope(prev_scope);
        try {
            while (true) {
                // visit condition
                Environment.PlcObject conditionValue = visit(ast.getCondition());
                Boolean condition = requireType(Boolean.class, conditionValue);
                if (!condition) {
                    break;
                }
                // Create scopes for loop bodies
                Scope body_scope = new Scope(this.scope);
                Scope prev_body_scope = this.scope;
                this.scope = body_scope;
                try {
                    // visit statements
                    for (Ast.Statement stmt : ast.getStatements()) {
                        visit(stmt);
                    }
                }
                finally {
                    // Restore body scope
                    this.scope = prev_body_scope;
                }
            }
        }
        finally {
            // Restore scope
            this.scope = prev_scope;
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
        return Environment.create(ast.getLiteral());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Group ast) {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Binary ast) {
        String operator = ast.getOperator();

        switch (operator) {
            case "&&": {
                Environment.PlcObject left = visit(ast.getLeft());
                Boolean left_val = requireType(Boolean.class, left);
                if (!left_val) {
                    return Environment.create(false);
                }
                else {
                    Environment.PlcObject right = visit(ast.getRight());
                    Boolean right_val = requireType(Boolean.class, right);
                    return Environment.create(right_val);
                }
            }
            case "||": {
                Environment.PlcObject left = visit(ast.getLeft());
                Boolean left_val = requireType(Boolean.class, left);
                if (left_val) {
                    return Environment.create(true);
                }
                else {
                    Environment.PlcObject right = visit(ast.getRight());
                    Boolean right_val = requireType(Boolean.class, right);
                    return Environment.create(right_val);
                }
            }
            case "<":
            case "<=":
            case ">":
            case ">=": {
                Environment.PlcObject left = visit(ast.getLeft());
                Environment.PlcObject right = visit(ast.getRight());

                Comparable<Object> left_val = requireType(Comparable.class, left);
                Object right_val = right.getValue();

                if (!left_val.getClass().equals(right_val.getClass())) {
                    throw new RuntimeException("Operands not of the same type.");
                }

                int comparison = left_val.compareTo(right_val);

                switch (operator) {
                    case "<":
                        return Environment.create(comparison < 0);
                    case "<=":
                        return Environment.create(comparison <= 0);
                    case ">":
                        return Environment.create(comparison > 0);
                    case ">=":
                        return Environment.create(comparison >= 0);
                }
            }
            case "==":
            case "!=": {
                Environment.PlcObject left = visit(ast.getLeft());
                Environment.PlcObject right = visit(ast.getRight());

                boolean is_equal = java.util.Objects.equals(left.getValue(), right.getValue());

                if (operator.equals("==")) {
                    return Environment.create(is_equal);
                }
                else {
                    return Environment.create(!is_equal);
                }
            }
            case "+": {
                Environment.PlcObject left = visit(ast.getLeft());
                Environment.PlcObject right = visit(ast.getRight());

                if (left.getValue() instanceof String || right.getValue() instanceof String) {
                    // concat string
                    String res = left.getValue().toString() + right.getValue().toString();
                    return Environment.create(res);
                }
                else if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger) {
                    BigInteger left_val = requireType(BigInteger.class, left);
                    BigInteger right_val = requireType(BigInteger.class, right);
                    return Environment.create(left_val.add(right_val));
                }
                else if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal) {
                    BigDecimal left_val = requireType(BigDecimal.class, left);
                    BigDecimal right_val = requireType(BigDecimal.class, right);
                    return Environment.create(left_val.add(right_val));
                }
                else {
                    throw new RuntimeException("Unsupported operands for '+'.");
                }
            }
            case "-":
            case "*":
            case "/": {
                Environment.PlcObject left = visit(ast.getLeft());
                Environment.PlcObject right = visit(ast.getRight());

                if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger) {
                    BigInteger left_val = requireType(BigInteger.class, left);
                    BigInteger right_val = requireType(BigInteger.class, right);

                    switch (operator) {
                        case "-":
                            return Environment.create(left_val.subtract(right_val));
                        case "*":
                            return Environment.create(left_val.multiply(right_val));
                        case "/":
                            if (right_val.equals(BigInteger.ZERO)) {
                                throw new RuntimeException("Cannot divide by zero.");
                            }
                            return Environment.create(left_val.divide(right_val));
                    }
                }
                else if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal) {
                    BigDecimal left_val = requireType(BigDecimal.class, left);
                    BigDecimal right_val = requireType(BigDecimal.class, right);

                    switch (operator) {
                        case "-":
                            return Environment.create(left_val.subtract(right_val));
                        case "*":
                            return Environment.create(left_val.multiply(right_val));
                        case "/":
                            if (right_val.compareTo(BigDecimal.ZERO) == 0) {
                                throw new RuntimeException("Cannot divide by zero.");
                            }
                            return Environment.create(left_val.divide(right_val, java.math.RoundingMode.HALF_EVEN));
                    }
                }
                else {
                    throw new RuntimeException("Unsupported operands for '" + operator + "'.");
                }
            }
            default:
                // exception handling for anything else
                throw new RuntimeException("Unsupported operator: " + operator);
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Access ast) {
        if (ast.getReceiver().isPresent()) {
            // visit receiver's object
            Environment.PlcObject rec_obj = visit(ast.getReceiver().get());

            // return obj's field value
            return rec_obj.getField(ast.getName()).getValue();
        }
        else {
            // Get variable from the current scope
            Environment.Variable variable = scope.lookupVariable(ast.getName());
            return variable.getValue();
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Function ast) {
        // visit arguments
        List<Environment.PlcObject> arguments = new ArrayList<>();
        for (Ast.Expression arg : ast.getArguments()) {
            arguments.add(visit(arg));
        }
        if (ast.getReceiver().isPresent()) {
            // visit receiver's object
            Environment.PlcObject rec_obj = visit(ast.getReceiver().get());
            // Call method
            return rec_obj.callMethod(ast.getName(), arguments);
        }
        else {
            // lookup function in the current scope
            Environment.Function function = scope.lookupFunction(ast.getName(), arguments.size());
            // call the function
            return function.invoke(arguments);
        }
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
