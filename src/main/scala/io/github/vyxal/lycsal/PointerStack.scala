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
    private var ctxVar1: Option[LLVMValueRef] = None
    private var ctxVar2: Option[LLVMValueRef] = None
    // I don't like this
    def initCtxVar1(ty: LLVMTypeRef)(using builder: LLVMBuilderRef) =
        ctxVar1 = Some(LLVMBuildAlloca(builder, ty, "ctxvar1alloc"))
    def initCtxVar2(ty: LLVMTypeRef)(using builder: LLVMBuilderRef) =
        ctxVar2 = Some(LLVMBuildAlloca(builder, ty, "ctxvar2alloc"))
    def setCtxVar1(value: TypedValueRef)(using builder: LLVMBuilderRef) =
        LLVMBuildStore(builder, value.ensuring(value.ty == LLVMGetAllocatedType(ctxVar1.get)).value, ctxVar1.get)
    def setCtxVar2(value: TypedValueRef)(using builder: LLVMBuilderRef) =
        LLVMBuildStore(builder, value.ensuring(value.ty == LLVMGetAllocatedType(ctxVar2.get)).value, ctxVar2.get)
    def getCtxVar1()(using builder: LLVMBuilderRef) =
        LLVMBuildLoad2(builder, LLVMGetAllocatedType(ctxVar1.get), ctxVar1.get, "ctxvar1")
    def getCtxVar2()(using builder: LLVMBuilderRef) =
        LLVMBuildLoad2(builder, LLVMGetAllocatedType(ctxVar2.get), ctxVar2.get, "ctxvar2")
    def clearCtxVar1() =
        ctxVar1 = None
    def clearCtxVar2() =
        ctxVar2 = None


    def popLoad()(using builder: LLVMBuilderRef) = 
        val pointer = this.pop()
        LLVMBuildLoad2(builder, pointer.ty, pointer.value, "load")
    def peekLoad(using builder: LLVMBuilderRef) =
        val pointer = this.top
        LLVMBuildLoad2(builder, pointer.ty, pointer.value, "load")
    def popLoadWithType()(using builder: LLVMBuilderRef) = 
        val pointer = this.pop()
        TypedValueRef(pointer.ty, LLVMBuildLoad2(builder, pointer.ty, pointer.value, "load"))
    def peekLoadWithType(using builder: LLVMBuilderRef) =
        val pointer = this.top
        TypedValueRef(pointer.ty, LLVMBuildLoad2(builder, pointer.ty, pointer.value, "load"))
    def pushStore(value: TypedValueRef)(using builder: LLVMBuilderRef) = 
        val pointer = LLVMBuildAlloca(builder, value.ty, "alloca")
        LLVMBuildStore(builder, value.value, pointer)
        this.push(TypedValueRef(value.ty, pointer))