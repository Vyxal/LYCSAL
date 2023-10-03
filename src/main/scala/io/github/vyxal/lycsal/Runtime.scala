package io.github.vyxal.lycsal

import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.javacpp.Pointer

type RuntimeFunction = Array[Pointer] => LLVMBuilderRef ?=> LLVMValueRef

// This class is only for utility functions that should
// be available at runtime.
class RuntimeFunctions(val context: LLVMContextRef, val module: LLVMModuleRef, val builder: LLVMBuilderRef, val ts: TypeSupplier):
    private object Wrapper:
        private def wrap(ty: LLVMTypeRef, fn: LLVMValueRef, variadic: Boolean): RuntimeFunction = 
            val nargs = LLVMCountParamTypes(ty)
            val name = if LLVMGetTypeKind(LLVMGetReturnType(ty)) == LLVMVoidTypeKind then "" else "invoke"
            return (arguments: Array[Pointer]) => (builder: LLVMBuilderRef) ?=> 
                if !variadic then assert(arguments.size == nargs, s"Argument list mismatch! Expected ${nargs} but got ${arguments.size}")
                LLVMBuildCall2(builder, ty, fn, PointerPointer(arguments.size).put(arguments*), arguments.size, name)
        private def wrap(returnType: LLVMTypeRef, argTypes: Array[LLVMTypeRef], fn: LLVMValueRef, variadic: Boolean): RuntimeFunction =
            val ty = LLVMFunctionType(returnType, PointerPointer(argTypes.size).put(argTypes*), argTypes.size, 0)
            wrap(ty, fn, variadic)
        private def wrap(name: String, returnType: LLVMTypeRef, argTypes: Array[LLVMTypeRef], variadic: Boolean = false)(impl: (LLVMValueRef) => Unit): RuntimeFunction =
            val ty = LLVMFunctionType(returnType, PointerPointer(argTypes.size).put(argTypes*), argTypes.size, 0)
            val fn = LLVMAddFunction(module, name, ty)
            impl(fn)
            wrap(ty, fn, variadic)
        // Only externs can be variadic. Maybe revist this in the future?
        def wrap(name: String, returnType: LLVMTypeRef, argTypes: Array[LLVMTypeRef])(impl: ((LLVMValueRef) => Unit)): RuntimeFunction = wrap(name, returnType, argTypes, false)(impl)
        def extern(name: String, returnType: LLVMTypeRef, argTypes: Array[LLVMTypeRef], variadic: Boolean = false): RuntimeFunction = wrap(name, returnType, argTypes, variadic)((_) => ())

    object libc:
        val puts = Wrapper.extern("puts", ts.i64, Array(ts.pointer))
        val sprintf = Wrapper.extern("sprintf", ts.i64, Array(ts.pointer, ts.pointer), true)

    val vystringify = Wrapper.wrap(
        "vystringify",
        ts.string,
        Array(ts.i8, ts.pointer)
    )((fn) =>
        given LLVMBuilderRef = builder
        given TypeSupplier = ts
        val entry = LLVMAppendBasicBlockInContext(context, fn, "entry")
        LLVMPositionBuilderAtEnd(builder, entry)
        val valueType = LLVMGetParam(fn, 0)
        val valuePtr = LLVMGetParam(fn, 1)
        val intPath = LLVMAppendBasicBlockInContext(context, fn, "int")
        val stringPath = LLVMAppendBasicBlockInContext(context, fn, "string")
        val arrayPath = LLVMAppendBasicBlockInContext(context, fn, "array")
        val fallthroughPath = LLVMAppendBasicBlockInContext(context, fn, "fallthrough")
        val switch = LLVMBuildSwitch(builder, valueType, fallthroughPath, 3)
        LLVMAddCase(switch, TypeTag.Number(), intPath)
        LLVMAddCase(switch, TypeTag.String(), intPath)
        LLVMPositionBuilderAtEnd(builder, intPath)
        val buf = LLVMBuildArrayAlloca(builder, ts.i8, LLVMConstInt(ts.i64, 20, 0), "buf")
        libc.sprintf(Array(buf, LLVMConstString("%d", 2, 0), valuePtr))
        LLVMBuildRet(builder, buf)
    )

    val vyprint = Wrapper.wrap(
        "vyprint",
        ts.void,
        Array(ts.i8, ts.pointer)
    )((fn) =>
        given LLVMBuilderRef = builder
        val entry = LLVMAppendBasicBlockInContext(context, fn, "entry")
        LLVMPositionBuilderAtEnd(builder, entry)
        val valueType = LLVMGetParam(fn, 0)
        val valuePtr = LLVMGetParam(fn, 1)
        libc.puts(Array(vystringify(Array(valueType, valuePtr))))
        LLVMBuildRetVoid(builder)
    )