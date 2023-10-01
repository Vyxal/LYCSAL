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

sealed case class TypedValueRef(ty: LLVMTypeRef, value: LLVMValueRef)
class TypeSupplier(context: LLVMContextRef, module: LLVMModuleRef):
    val i32 = LLVMInt32TypeInContext(context)
    val i64 = LLVMInt64TypeInContext(context)
    val i1 = LLVMInt1TypeInContext(context)
    val i8 = LLVMInt8TypeInContext(context)
    val pointer = LLVMPointerTypeInContext(context, 0) // hopefully I got that last parameter right
    def platformint = i64 // TODO

    val void = LLVMVoidTypeInContext(context)

    val arrayItem = LLVMStructTypeInContext(context, PointerPointer(2).put(0, i8).put(1, i64), 2, 1)
    val numberArrayItem = LLVMStructTypeInContext(context, PointerPointer(2).put(0, i8).put(1, i64), 2, 1)
    val pointerArrayItem = LLVMStructTypeInContext(context, PointerPointer(2).put(0, i8).put(1, pointer), 2, 1)
    val arrayItemSize = 64 + 8
    val array = LLVMArrayType(arrayItem, 0)
    val string = LLVMArrayType(i8, 0)

    object libc:
        // All libc external functions go here, not in RuntimeFunctions
        val putsType = LLVMFunctionType(i64, PointerPointer(1).put(0, pointer), 1, 0)
        val puts = LLVMAddFunction(module, "puts", putsType)

        def puts(pointer: LLVMValueRef)(using builder: LLVMBuilderRef): Unit =
            LLVMBuildCall2(builder, putsType, puts, PointerPointer(1).put(pointer), 1, "puts")