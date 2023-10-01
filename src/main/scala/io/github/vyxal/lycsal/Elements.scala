package io.github.vyxal.lycsal

import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*
import org.bytedeco.javacpp.PointerPointer

case class Element(
    symbol: String,
    name: String,
    arity: Int,
    compile: DirectFn
)


class Elements(val builder: LLVMBuilderRef, val ts: TypeSupplier, val util: RuntimeFunctions):
    given LLVMBuilderRef = builder
    val elements: Map[String, Element] = Map(
        // This one needs to be direct since puts() requires a pointer
        direct("p", "print", 1)((pointers: PointerStack) ?=> {
            LLVMBuildCall2(builder, util.vyprintType, util.vyprint, PointerPointer(1).put(pointers.pop().value), 1, "")
            List()
        }),
        element(Dyad, "+", "add")((lhs: TypedValueRef, rhs: TypedValueRef) => {
            Some(TypedValueRef(ts.i64, LLVMBuildAdd(builder, lhs.value, rhs.value, "add")))
        })
    )


    private def element[F](
        implementer: Implementer[F],
        symbol: String,
        name: String
    )(impl: F)(using builder: LLVMBuilderRef): (String, Element) =
        symbol -> Element(symbol, name, implementer.arity, implementer.directify(impl))
    
    private def direct(symbol: String, name: String, arity: Int)(impl: PointerStack ?=> List[TypedValueRef]) =
        symbol -> Element(symbol, name, arity, () => impl)

    private def nilad(symbol: String, name: String)(impl: () => TypedValueRef) =
        symbol -> Element(symbol, name, 0, () => PointerStack ?=> List(impl()))