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

class PointerStack extends Stack[TypedValueRef]:
    def popLoad()(using builder: LLVMBuilderRef, ts: TypeSupplier) = 
        val pointer = this.pop()
        LLVMBuildLoad2(builder, pointer.ty.underlying, pointer.value, "load")
    def peekLoad(using builder: LLVMBuilderRef, ts: TypeSupplier) =
        val pointer = this.top
        LLVMBuildLoad2(builder, pointer.ty.underlying, pointer.value, "load")
    def popLoadWithType()(using builder: LLVMBuilderRef, ts: TypeSupplier) = 
        val pointer = this.pop()
        TypedValueRef(pointer.ty, LLVMBuildLoad2(builder, pointer.ty.underlying, pointer.value, "load"))
    def peekLoadWithType(using builder: LLVMBuilderRef, ts: TypeSupplier) =
        val pointer = this.top
        TypedValueRef(pointer.ty, LLVMBuildLoad2(builder, pointer.ty.underlying, pointer.value, "load"))
    def pushStore(value: TypedValueRef)(using builder: LLVMBuilderRef, ts: TypeSupplier) = 
        val pointer = LLVMBuildAlloca(builder, value.ty.underlying, "alloca")
        LLVMBuildStore(builder, value.value, pointer)
        this.push(TypedValueRef(value.ty, pointer))