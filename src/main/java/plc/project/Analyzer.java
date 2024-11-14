package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {
    public Scope scope;
    private Ast.Method method;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        // visit the fields in getFields()
        for (Ast.Field field : ast.getFields()) {
            visit(field);
        }

        // visit the methods in getMethods()
        for (Ast.Method method : ast.getMethods()) {
            visit(method);
        }

        // check if main has int return type
        try {
            Environment.Function main_func = scope.lookupFunction("main", 0);
            if (!main_func.getReturnType().equals(Environment.Type.INTEGER)) {
                throw new RuntimeException("The main function must have the return type of Integer.");
            }
        }
        catch (RuntimeException e) {
            throw new RuntimeException("The main function is not defined.");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        // Get the field type from the environment
        Environment.Type field_type;
        try {
            field_type = Environment.getType(ast.getTypeName());
        }
        catch (RuntimeException e) {
            throw new RuntimeException("Undefined type: " + ast.getTypeName());
        }

        // visit the value if it is present
        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
            requireAssignable(field_type, ast.getValue().get().getType());
        }
        else {
            // when it is a constant
            if (ast.getConstant()) {
                throw new RuntimeException("The constant '" + ast.getName() + "' must have an initial value.");
            }
        }

        // define in scope
        Environment.Variable variable = scope.defineVariable(
                ast.getName(),
                ast.getName(),
                field_type,
                ast.getConstant(),
                Environment.NIL
        );
        ast.setVariable(variable);

        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        // get a list of parameter types from the ast
        List<Environment.Type> param_types = new ArrayList<>();
        for (String type_name : ast.getParameterTypeNames()) {
            try {
                param_types.add(Environment.getType(type_name));
            }
            catch (RuntimeException e) {
                throw new RuntimeException("Undefined parameter: " + type_name);
            }
        }

        // get type of return given that it's present
        Environment.Type return_type;
        if (ast.getReturnTypeName().isPresent()) {
            try {
                return_type = Environment.getType(ast.getReturnTypeName().get());
            }
            catch (RuntimeException e) {
                throw new RuntimeException("Undefined return: " + ast.getReturnTypeName().get());
            }
        }
        else {
            return_type = Environment.Type.NIL;
        }

        // define in scope
        Environment.Function function = scope.defineFunction(
                ast.getName(),
                ast.getName(),
                param_types,
                return_type,
                args -> Environment.NIL
        );

        ast.setFunction(function);

        // create new scope for the method (diff scope than what was just defined)
        Scope prev_scope = this.scope;
        this.scope = new Scope(prev_scope);
        try {
            // parameters in the new scope
            for (int i = 0; i < ast.getParameters().size(); i++) {
                String param_name = ast.getParameters().get(i);
                Environment.Type param_type = param_types.get(i);
                Environment.Variable variable = scope.defineVariable(
                        param_name,
                        param_name,
                        param_type,
                        false,
                        Environment.NIL
                );
            }

            Ast.Method prev_method = this.method;
            this.method = ast;

            // visit all the statements in the ast
            for (Ast.Statement stmt : ast.getStatements()) {
                visit(stmt);
            }

            this.method = prev_method;
        }
        finally {
            // restore scope
            this.scope = prev_scope;
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        if (!(ast.getExpression() instanceof Ast.Expression.Function)) {
            throw new RuntimeException("Expression statements must be function calls.");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        Environment.Type variable_type = null;
        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
            variable_type = ast.getValue().get().getType();
        }

        // get type from the environment
        if (ast.getTypeName().isPresent()) {
            try {
                Environment.Type type = Environment.getType(ast.getTypeName().get());
                if (variable_type != null) {
                    // Check if value is assignable to declared type
                    requireAssignable(type, variable_type);
                }
                variable_type = type;
            } catch (RuntimeException e) {
                throw new RuntimeException("Undefined type: " + ast.getTypeName().get());
            }
        }
        else if (variable_type == null) {
            throw new RuntimeException("Cannot declare variable '" + ast.getName() + "' without type or value.");
        }

        // current scope
        Environment.Variable variable = scope.defineVariable(
                ast.getName(),
                ast.getName(),
                variable_type,
                false,
                Environment.NIL
        );

        ast.setVariable(variable);

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        // check that the receiver is open to the ast's access
        if (!(ast.getReceiver() instanceof Ast.Expression.Access)) {
            throw new RuntimeException("Invalid target for assignment.");
        }

        visit(ast.getReceiver());
        visit(ast.getValue());

        // get variable from receiver
        Ast.Expression.Access access = (Ast.Expression.Access) ast.getReceiver();
        Environment.Variable variable = access.getVariable();

        // check for constance and initialization
        if (variable.getConstant()) {
            throw new RuntimeException("Cannot assign constant to '" + variable.getName() + "'.");
        }

        requireAssignable(variable.getType(), ast.getValue().getType());

        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        visit(ast.getCondition());

        // check that the condition is Boolean
        if (!ast.getCondition().getType().equals(Environment.Type.BOOLEAN)) {
            throw new RuntimeException("Condition of the IF statement must be of type Boolean.");
        }

        // then cannot be empty when there is an if statement present (else can be empty)
        if (ast.getThenStatements().isEmpty()) {
            throw new RuntimeException("THEN statements of IF statement cannot be empty.");
        }

        // change the scope upon visiting the then statement
        Scope prev_scope = this.scope;
        this.scope = new Scope(prev_scope);
        try {
            for (Ast.Statement stmt : ast.getThenStatements()) {
                visit(stmt);
            }
        }
        finally {
            this.scope = prev_scope;
        }

        // do the same as ^ on else statement
        if (!ast.getElseStatements().isEmpty()) {
            this.scope = new Scope(prev_scope);
            try {
                for (Ast.Statement stmt : ast.getElseStatements()) {
                    visit(stmt);
                }
            }
            finally {
                this.scope = prev_scope;
            }
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.For ast) {
        // new for loop scope
        Scope prev_scope = this.scope;
        this.scope = new Scope(prev_scope);
        try {
            if (ast.getInitialization() != null) {
                visit(ast.getInitialization());
                // check the initialization is a declaration and that it is of the Comparable type
                if (ast.getInitialization() instanceof Ast.Statement.Declaration) {
                    Ast.Statement.Declaration declaration = (Ast.Statement.Declaration) ast.getInitialization();
                    if (!declaration.getVariable().getType().getJvmName().equals("Comparable")) {
                        throw new RuntimeException("Variable in for loop must be Comparable.");
                    }
                }
                else if (ast.getInitialization() instanceof Ast.Statement.Assignment) {
                    Ast.Statement.Assignment assignment = (Ast.Statement.Assignment) ast.getInitialization();
                    if (!assignment.getValue().getType().getJvmName().equals("Comparable")) {
                        throw new RuntimeException("Variable in for loop must be Comparable.");
                    }
                }
            }

            visit(ast.getCondition());
            if (!ast.getCondition().getType().equals(Environment.Type.BOOLEAN)) {
                throw new RuntimeException("For loop's conditions must be Boolean.");
            }

            if (ast.getIncrement() != null) {
                visit(ast.getIncrement()); // check the increment
            }

            // loop body cannot be empty, checks for this
            if (ast.getStatements().isEmpty()) {
                throw new RuntimeException("For loop body cannot be empty.");
            }

            // new scope for the for loop body
            Scope body_scope = new Scope(this.scope);
            Scope temp = this.scope;
            this.scope = body_scope;
            try {
                for (Ast.Statement stmt : ast.getStatements()) {
                    visit(stmt);
                }
            }
            finally {
                this.scope = temp;
            }
        }
        finally {
            this.scope = prev_scope;
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        visit(ast.getCondition());

        // check the condition like the for loops
        if (!ast.getCondition().getType().equals(Environment.Type.BOOLEAN)) {
            throw new RuntimeException("While loop condition must be of type Boolean.");
        }

        // create new scope for the loop body
        Scope prev_scope = this.scope;
        this.scope = new Scope(prev_scope);
        try {
            for (Ast.Statement stmt : ast.getStatements()) {
                visit(stmt);
            }
        } finally {
            this.scope = prev_scope;
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        if (this.method == null) { // check bounds and ensure that the method is not null
            throw new RuntimeException("No Return statement in this method.");
        }
        visit(ast.getValue());
        requireAssignable(this.method.getFunction().getReturnType(), ast.getValue().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        Object value = ast.getLiteral();
        if (value == null) {
            ast.setType(Environment.Type.NIL);
        }
        else if (value instanceof Boolean) {
            ast.setType(Environment.Type.BOOLEAN);
        }
        else if (value instanceof Character) {
            ast.setType(Environment.Type.CHARACTER);
        }
        else if (value instanceof String) {
            ast.setType(Environment.Type.STRING);
        }
        else if (value instanceof BigInteger) {
            BigInteger big_int = (BigInteger) value;
            if (big_int.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0 ||
                    big_int.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                throw new RuntimeException("Out of range Integer literal.");
            }
            ast.setType(Environment.Type.INTEGER);
        }
        else if (value instanceof BigDecimal) {
            BigDecimal big_decimal = (BigDecimal) value;
            double double_val = big_decimal.doubleValue();
            if (Double.isInfinite(double_val) || Double.isNaN(double_val)) {
                throw new RuntimeException("Out of range Decimal literal.");
            }
            ast.setType(Environment.Type.DECIMAL);
        }
        else {
            throw new RuntimeException("Unknown literal.");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        visit(ast.getExpression());

        // if expression is not binary
        if (!(ast.getExpression() instanceof Ast.Expression.Binary)) {
            throw new RuntimeException("Group expression must contain a binary expression.");
        }

        // set the type as the expressions type
        ast.setType(ast.getExpression().getType());

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        String op = ast.getOperator();

        visit(ast.getLeft());
        visit(ast.getRight());

        Environment.Type left_type = ast.getLeft().getType();
        Environment.Type right_type = ast.getRight().getType();

        switch (op) {
            case "&&":
            case "||":
                if (!left_type.equals(Environment.Type.BOOLEAN) || !right_type.equals(Environment.Type.BOOLEAN)) {
                    throw new RuntimeException("Boolean operands are required on both sides of the Boolean operator.");
                }
                ast.setType(Environment.Type.BOOLEAN);
                break;
            case "<":
            case "<=":
            case ">":
            case ">=":
            case "==":
            case "!=":
                if (!isComparablType(left_type) || !isComparablType(right_type) || !left_type.equals(right_type)) {
                    throw new RuntimeException("Operands of the same type are required for comparison operators.");
                }
                ast.setType(Environment.Type.BOOLEAN);
                break;
            case "+":
                if (left_type.equals(Environment.Type.STRING) || right_type.equals(Environment.Type.STRING)) {
                    ast.setType(Environment.Type.STRING);
                }
                else if (left_type.equals(Environment.Type.INTEGER) && right_type.equals(Environment.Type.INTEGER)) {
                    ast.setType(Environment.Type.INTEGER);
                }
                else if (left_type.equals(Environment.Type.DECIMAL) && right_type.equals(Environment.Type.DECIMAL)) {
                    ast.setType(Environment.Type.DECIMAL);
                }
                else {
                    throw new RuntimeException("Invalid operands for '+'.");
                }
                break;
            case "-":
            case "*":
            case "/":
                if (left_type.equals(Environment.Type.INTEGER) && right_type.equals(Environment.Type.INTEGER)) {
                    ast.setType(Environment.Type.INTEGER);
                }
                else if (left_type.equals(Environment.Type.DECIMAL) && right_type.equals(Environment.Type.DECIMAL)) {
                    ast.setType(Environment.Type.DECIMAL);
                }
                else {
                    throw new RuntimeException("Invalid operands for '" + op + "'.");
                }
                break;
            default:
                throw new RuntimeException("Unknown operator '" + op + "'.");
        }

        return null;
    }

    private boolean isComparablType(Environment.Type type) { // helper function for visiting the binary ast
        return type.equals(Environment.Type.INTEGER) ||
                type.equals(Environment.Type.DECIMAL) ||
                type.equals(Environment.Type.CHARACTER) ||
                type.equals(Environment.Type.STRING);

    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        if (ast.getReceiver().isPresent()) {
            visit(ast.getReceiver().get());
            Environment.Type receiver_type = ast.getReceiver().get().getType();
            try {
                Environment.Variable variable = receiver_type.getField(ast.getName());
                ast.setVariable(variable);
            }
            catch (RuntimeException e) {
                throw new RuntimeException("Field, '" + ast.getName() + ",' not found in the type '" + receiver_type.getName() + "'.");
            }
        }
        else {
            try {
                Environment.Variable variable = scope.lookupVariable(ast.getName());
                ast.setVariable(variable);
            }
            catch (RuntimeException e) {
                throw new RuntimeException("Variable '" + ast.getName() + "' not found in the current scope.");
            }
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        List<Environment.Type> arg_types = new ArrayList<>();

        // visit the args and get their types
        for (Ast.Expression arg : ast.getArguments()) {
            visit(arg);
            arg_types.add(arg.getType());
        }

        if (ast.getReceiver().isPresent()) {
            visit(ast.getReceiver().get());
            Environment.Type receiver_type = ast.getReceiver().get().getType();
            try {
                Environment.Function function = receiver_type.getFunction(ast.getName(), arg_types.size());
                ast.setFunction(function);

                List<Environment.Type> param_types = function.getParameterTypes();
                for (int i = 0; i < arg_types.size(); i++) {
                    requireAssignable(param_types.get(i + 1), arg_types.get(i));
                }
            }
            catch (RuntimeException e) {
                throw new RuntimeException("Method, '" + ast.getName() + ",' not found in type '" + receiver_type.getName() + "'.");
            }
        }
        else {
            // within the current scope
            try {
                Environment.Function function = scope.lookupFunction(ast.getName(), arg_types.size());
                ast.setFunction(function);
                List<Environment.Type> param_types = function.getParameterTypes();
                for (int i = 0; i < arg_types.size(); i++) {
                    requireAssignable(param_types.get(i), arg_types.get(i));
                }
            }
            catch (RuntimeException e) {
                throw new RuntimeException("Function, '" + ast.getName() + ",' with arity " + arg_types.size() + " not found in current scope.");
            }
        }

        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        if (target.equals(type)) { // types are the same, =
            return;
        }
        else if (target.equals(Environment.Type.ANY)) { // target is any type
            return;
        }
        else if (target.equals(Environment.Type.COMPARABLE)) { // if target is any comparable type
            if (type.equals(Environment.Type.INTEGER) ||
                    type.equals(Environment.Type.DECIMAL) ||
                    type.equals(Environment.Type.CHARACTER) ||
                    type.equals(Environment.Type.STRING)) {
                return;
            }
        }
        // else there is no assignable type sent through this method
        throw new RuntimeException("Type, '" + type.getName() + ",' is not assignable to '" + target.getName() + "'.");
    }
}
