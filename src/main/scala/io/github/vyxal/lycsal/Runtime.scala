package io.github.vyxal.lycsal

import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*
import org.bytedeco.javacpp.PointerPointer

// This class is only for utility functions that should
// be available at runtime. Put libc externs in
// TypeSupplier.libc instead.
class RuntimeFunctions(val context: LLVMContextRef, val module: LLVMModuleRef, val builder: LLVMBuilderRef, val ts: TypeSupplier):
    val vyprintType = LLVMFunctionType(ts.void, PointerPointer(1).put(ts.pointer), 1, 0)

    private def vyprintImpl =
        given LLVMBuilderRef = builder
        val fn = LLVMAddFunction(module, "vyprint", vyprintType)
        val entry = LLVMAppendBasicBlockInContext(context, fn, "entry")
        LLVMPositionBuilderAtEnd(builder, entry)
        val stringPointer = LLVMGetParam(fn, 0)
        ts.libc.puts(stringPointer)
        LLVMBuildRetVoid(builder)
        fn

    val vyprint = vyprintImpl