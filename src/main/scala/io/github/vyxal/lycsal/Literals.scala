package io.github.vyxal.lycsal

import vyxal.VNum
import org.bytedeco.llvm.LLVM.LLVMBuilderRef
import org.bytedeco.llvm.global.LLVM.*
import org.bytedeco.llvm.LLVM.LLVMContextRef
import org.bytedeco.llvm.LLVM.LLVMValueRef
import org.bytedeco.llvm.LLVM.LLVMTypeRef
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.javacpp.IntPointer

class Literals(ts: TypeSupplier):
    given TypeSupplier = ts
    def generateNumber(value: VNum)(using pointers: PointerStack, builder: LLVMBuilderRef) =
        if value.isValidInt then
            pointers.pushStore(TypedValueRef(TypeTag.Number, LLVMConstInt(ts.i64, value.toLong, 0)))
        else
            throw Error("Non-integer number")

    // def appendArrayItem(arrayIn: LLVMValueRef, value: LLVMValueRef, valueTypeIn: LLVMTypeRef)(using pointers: PointerStack, builder: LLVMBuilderRef, context: LLVMContextRef, scope: Scope) =
    //     val valueType = valueTypeIn match
    //         case ts.i64 => ts.numberArrayItem
    //         case ts.pointer => ts.pointerArrayItem
    //         case _ => throw Error(s"type bad")
    //     // load array fields
    //     val arrayInfo = LLVMBuildCall2(builder, util.eainType, util.eain, PointerPointer(2).put(arrayIn).put(LLVMConstInt(ts.i64, 1, 0)), 1, "eain")
    //     val nItemsPtr = LLVMBuildStructGEP2(builder, ts.array, arrayInfo, 0, "nItemsPtr")
    //     val arrayPtrPtr = LLVMBuildStructGEP2(builder, ts.array, arrayInfo, 2, "arrayPtrPtr")
    //     val nItems = LLVMBuildLoad2(builder, ts.i64, nItemsPtr, "nItems")
    //     val arrayPtr = LLVMBuildLoad2(builder, ts.pointer, arrayPtrPtr, "arrayPtr")
    //     // generate the new struct
    //     val newItem = LLVMBuildAlloca(builder, valueType, "newItem")
    //     // and set its fields
    //     val newItemTagPtr = LLVMBuildStructGEP2(builder, ts.i8, newItem, 0, "newItemTagPtr")
    //     val newItemValuePtr = LLVMBuildStructGEP2(builder, valueTypeIn, newItem, 1, "newItemValuePtr")
    //     LLVMBuildStore(builder, LLVMConstInt(ts.i8, valueType match
    //         case ts.numberArrayItem => 0
    //         case ts.pointerArrayItem => 1
    //     , 0), newItemTagPtr)
    //     LLVMBuildStore(builder, value, newItemValuePtr)
    //     // finally, save the struct to the array
    //     val newItemPtr = LLVMBuildGEP2(builder, ts.arrayItem, arrayPtr, PointerPointer(1).put(nItems), 1, "newItemPtr")
    //     LLVMBuildStore(builder, newItem, newItemPtr)
    //     ()

    // def generateArray(items: Int)(using pointers: PointerStack, builder: LLVMBuilderRef, context: LLVMContextRef) =
    //     pointers.pushStore(ts.pointer, ts.libc.malloc(LLVMConstInt(ts.platformint, ts.arrayItemSize * items, 0)))
    // def generateArray(items: Seq[(LLVMTypeRef, LLVMValueRef)])(using pointers: PointerStack, builder: LLVMBuilderRef, context: LLVMContextRef, scope: Scope) =
    //     val array = ts.libc.malloc(LLVMConstInt(ts.platformint, ts.arrayItemSize * items.length, 0))
    //     for (valueType, value) <- items do
    //         appendArrayItem(array, value, valueType)

    // def generateString(value: String)(using pointers: PointerStack, builder: LLVMBuilderRef, context: LLVMContextRef, scope: Scope) = 
    //     generateArray(Stream.continually(ts.i8).zip(value.getBytes().map(LLVMConstInt(ts.i8, _, 0))))

    def generateArray(items: Seq[TypedValueRef])(using pointers: PointerStack, builder: LLVMBuilderRef, context: LLVMContextRef) =
        val size = LLVMConstInt(ts.i64, items.size, 0)
        val arrayPtr = LLVMBuildArrayAlloca(builder, ts.arrayItem, size, "allocarray")
        pointers.push(TypedValueRef(TypeTag.Array, arrayPtr))
        items.map(_._2).zipWithIndex.map((value, i) => {
            (value, LLVMBuildGEP2(builder, ts.arrayItem, arrayPtr, PointerPointer(1).put(LLVMConstInt(ts.i64, i, 0)), 1, s"set$i"))
        }).foreach(LLVMBuildStore(builder, _, _))

    def generateString(value: String)(using pointers: PointerStack, builder: LLVMBuilderRef, context: LLVMContextRef) =
        val size = LLVMConstInt(ts.i64, value.length + 1, 0)
        val arrayPtr = LLVMBuildArrayAlloca(builder, ts.i8, size, "allocstr")
        LLVMBuildStore(builder, LLVMConstString(value, value.length, 0), arrayPtr)
        pointers.push(TypedValueRef(TypeTag.Array, arrayPtr))