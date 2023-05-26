import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.CharStreams

fun parseString(input: String): Prog {
    val lexer = PucLexer(CharStreams.fromString(input))
    return parseInner(lexer)
}

fun parseFile(fileName: String): Prog {
    val lexer = PucLexer(CharStreams.fromFileName(fileName))
    return parseInner(lexer)
}

fun parseType(input: String): Type {
    val lexer = PucLexer(CharStreams.fromString(input))
    val tokens = CommonTokenStream(lexer)
    val parser = PucParser(tokens)
    return TypeVisitor().visit(parser.type())
}

fun parseInner(lexer: PucLexer): Prog {
    val tokens = CommonTokenStream(lexer)
    val parser = PucParser(tokens)
    val tree = parser.init()
    return ProgVisitor().visit(tree)
}

class ProgVisitor(): PucBaseVisitor<Prog>() {
    override fun visitProg(ctx: PucParser.ProgContext): Prog {
        val fnDefs = ctx.fnDef().map { FnDefVisitor().visit(it) }
        val typeDefs = ctx.typeDef().map { TypeDefVisitor().visit(it) }
        val expr = ExprVisitor().visit(ctx.expr())
        return Prog(typeDefs, fnDefs, expr)
    }
}

class FnDefVisitor(): PucBaseVisitor<FnDef>() {
    override fun visitFnDef(ctx: PucParser.FnDefContext): FnDef {
        val name = ctx.name.text
        val param = ctx.param.text
        val tyParam = TypeVisitor().visit(ctx.tyParam)
        val tyResult = TypeVisitor().visit(ctx.tyResult)
        val body = ExprVisitor().visit(ctx.body)
        return FnDef(name, Expr.Lambda(param, tyParam, body), Type.Function(tyParam, tyResult))
    }
}

class TypeDefVisitor(): PucBaseVisitor<TypeDef>() {
    override fun visitTypeDef(ctx: PucParser.TypeDefContext): TypeDef {
        val name = ctx.name.text
        val constructors = ctx.typeConstructor().map {
            val constr = it.UP_NAME().text
            val fields = it.type().map { ty -> TypeVisitor().visit(ty) }
            TypeConstructor(constr, fields)
        }
        return TypeDef(name, constructors)
    }
}

class PatternVisitor(): PucBaseVisitor<Pattern>() {
    override fun visitPattern(ctx: PucParser.PatternContext): Pattern {
        val type = ctx.typ.text
        val name = ctx.constr.text
        val fields = ctx.NAME().map { it.text }
        return Pattern.Constructor(type, name, fields)
    }
}

class TypeVisitor(): PucBaseVisitor<Type>() {
    override fun visitTyBool(ctx: PucParser.TyBoolContext?): Type {
        return Type.Bool
    }

    override fun visitTyInt(ctx: PucParser.TyIntContext?): Type {
        return Type.Integer
    }

    override fun visitTyText(ctx: PucParser.TyTextContext?): Type {
        return Type.Text
    }

    override fun visitTyParenthesized(ctx: PucParser.TyParenthesizedContext): Type {
        return visit(ctx.inner)
    }

    override fun visitTyConstructor(ctx: PucParser.TyConstructorContext): Type {
        val name = ctx.UP_NAME().text
        return Type.Constructor(name)
    }

    override fun visitTyFun(ctx: PucParser.TyFunContext): Type {
        val tyArg = visit(ctx.arg)
        val tyResult = visit(ctx.result)
        return Type.Function(tyArg, tyResult)
    }
}

class ExprVisitor(): PucBaseVisitor<Expr>() {

    override fun visitVar(ctx: PucParser.VarContext): Expr {
        return Expr.Var(ctx.NAME().text)
    }

    override fun visitIntLit(ctx: PucParser.IntLitContext): Expr {
        val int = ctx.INT().text.toInt()
        return Expr.Lit(Primitive.Integer(int))
    }

    override fun visitBoolLit(ctx: PucParser.BoolLitContext): Expr {
        val bool = ctx.BOOL_LIT().text == "true"
        return Expr.Lit(Primitive.Bool(bool))
    }

    override fun visitTextLit(ctx: PucParser.TextLitContext): Expr {
        val text = ctx.TEXT_LIT().text
            .trimStart('"')
            .trimEnd('"')
            .replace("\\n", "\n")

        return Expr.Lit(Primitive.Text(text))
    }

    override fun visitLambda(ctx: PucParser.LambdaContext): Expr {
        val param = ctx.param.text
        val tyParam = TypeVisitor().visit(ctx.tyParam)
        val body = this.visit(ctx.body)
        return Expr.Lambda(param, tyParam, body)
    }

    override fun visitApp(ctx: PucParser.AppContext): Expr {
        val fn = this.visit(ctx.fn)
        val arg = this.visit(ctx.arg)
        return Expr.App(fn, arg)
    }

    override fun visitParenthesized(ctx: PucParser.ParenthesizedContext): Expr {
        return this.visit(ctx.inner)
    }

    override fun visitConstruction(ctx: PucParser.ConstructionContext): Expr {
        val type = ctx.typ.text
        val name = ctx.constr.text
        val fields = ctx.expr().map { this.visit(it) }
        return Expr.Construction(type, name, fields)
    }

    override fun visitCase(ctx: PucParser.CaseContext): Expr {
        val scrutinee = this.visit(ctx.scrutinee)
        val branches = ctx.caseBranch().map {
            CaseBranch(PatternVisitor().visit(it.pattern()), this.visit(it.body))
        }
        return Expr.Case(scrutinee, branches)
    }

    override fun visitIf(ctx: PucParser.IfContext): Expr {
        val condition = this.visit(ctx.condition)
        val thenBranch = this.visit(ctx.thenBranch)
        val elseBranch = this.visit(ctx.elseBranch)
        return Expr.If(condition, thenBranch, elseBranch)
    }

    override fun visitLet(ctx: PucParser.LetContext): Expr {
        val name = ctx.NAME().text
        val bound = this.visit(ctx.bound)
        val body = this.visit(ctx.body)

        return Expr.Let(name, bound, body)
    }

    override fun visitBinary(ctx: PucParser.BinaryContext): Expr {
        val left = this.visit(ctx.left)
        val op = when(ctx.op.text) {
            "+" -> Operator.Add
            "-" -> Operator.Sub
            "*" -> Operator.Mul
            "/" -> Operator.Div
            "==" -> Operator.Eq
            "||" -> Operator.Or
            "&&" -> Operator.And
            "++" -> Operator.Concat
            else -> throw Error("Unknown operator")
        }
        val right = this.visit(ctx.right)
        return Expr.Binary(left, op, right)
    }
}