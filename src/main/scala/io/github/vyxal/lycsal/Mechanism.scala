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

class Scope(val parent: Option[Scope], val function: LLVMValueRef):
    val pointers = PointerStack()
    def child = Scope(Some(this), this.function)
    def child(function: LLVMValueRef) = Scope(Some(this), function)

def i1ify(value: TypedValueRef)(using ts: TypeSupplier, builder: LLVMBuilderRef) =
    if value.ty != ts.i1 then
        LLVMBuildICmp(builder, LLVMIntNE, value.value, LLVMConstInt(ts.i64, 0, 0), "ne")
    else
        value.value