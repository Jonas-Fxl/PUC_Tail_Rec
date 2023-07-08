import kotlin.reflect.typeOf

fun main() {
    val parsed = parseFile("compiler/Tail_Recursion/factorial.puc")  //factorial,
    val (type, errors) = Typechecker().inferProg(parsed)
    errors.forEach { println(it) }
    val evaled = closureEval(parsed)
    print("$evaled")
    println(": ${type.print()}")
}