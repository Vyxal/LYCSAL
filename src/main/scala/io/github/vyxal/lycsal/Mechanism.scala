// Because Util.scala sounds boring
package io.github.vyxal.lycsal

import scala.collection.mutable.Stack
import org.bytedeco.llvm.LLVM.LLVMValueRef
import org.bytedeco.llvm.LLVM.LLVMBuilderRef
import org.bytedeco.llvm.global.LLVM.*
import org.bytedeco.llvm.LLVM.LLVMTypeRef
import org.bytedeco.llvm.LLVM.LLVMContextRef
import org.bytedeco.llvm.LLVM.LLVMTargetRef
import org.bytedeco.llvm.LLVM.LLVMTargetDataRef
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.llvm.LLVM.LLVMModuleRef
import scala.collection.mutable

// Note to self: might be worth implementing some utility functions
// for ensure()ing that LLVMValueRefs are the types they should be
// (probably via LLVMGetTypeKind)

class Scope private(val parent: Option[Scope], val function: LLVMValueRef, val pointers: PointerStack):
    private val ctxVars: mutable.Map[Int, CtxVarAccessor] = mutable.Map()

    def getCtxVar(index: Integer): Option[TypedValueRef] = ctxVars.get(index).map(_.get)
    def withCtxVar(index: Integer, ty: TypeTag)(impl: (CtxVarAccessor) => Unit)(using builder: LLVMBuilderRef, ts: TypeSupplier) = 
        val accessor = CtxVarAccessor(ty, builder, ts)
        ctxVars.ensuring(!ctxVars.contains(index)).update(index, accessor)
        impl(accessor)

    def child = Scope(Some(this), this.function, PointerStack())
    def child(function: LLVMValueRef) = Scope(Some(this), function, PointerStack())

    protected class CtxVarAccessor(private val ty: TypeTag, val builder: LLVMBuilderRef, val ts: TypeSupplier):
        // TODO This is absolutely a bad idea! It'll crash if the underlying type is an array/string,
        // which is why I'll be refactoring it very soon. For now, I'd just like to get this commit out.
        private val pointer = LLVMBuildAlloca(builder, ty.underlying(using ts), "allocctx")

        def set(value: LLVMValueRef): Unit = LLVMBuildStore(builder, value, pointer)
        def get = TypedValueRef(ty, LLVMBuildLoad2(builder, ty.underlying(using ts), pointer, "load"))
    
object Scope:
    def root(function: LLVMValueRef) = Scope(None, function, PointerStack())

def i1ify(value: TypedValueRef)(using ts: TypeSupplier, builder: LLVMBuilderRef) =
    if value.ty != ts.i1 then
        LLVMBuildICmp(builder, LLVMIntNE, value.value, LLVMConstInt(ts.i64, 0, 0), "ne")
    else
        value.value