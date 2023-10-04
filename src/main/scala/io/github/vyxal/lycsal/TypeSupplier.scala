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

enum TypeTag:
    case Number
    case Boolean
    case String
    case Array

    def tag(using ts: TypeSupplier): LLVMValueRef = LLVMConstInt(ts.i8, this.ordinal, 0)

enum NumberFormat:
    case i32
    case i64
    case byte
    case double
    def underlying(using ts: TypeSupplier) = this match
        case NumberFormat.i32 => ts.i32
        case NumberFormat.i64 => ts.i64
        case NumberFormat.byte => ts.i8
        case NumberFormat.double => ts.double

sealed abstract class TypeSpec(tag: TypeTag):
    def alloca(using builder: LLVMBuilderRef, ts: TypeSupplier): LLVMValueRef
    def underlying(using ts: TypeSupplier): LLVMTypeRef
    def tag(using ts: TypeSupplier): LLVMValueRef = tag.tag
case class NumberTypeSpec(format: NumberFormat) extends TypeSpec(TypeTag.Number):
    override def alloca(using builder: LLVMBuilderRef, ts: TypeSupplier): LLVMValueRef = 
        LLVMBuildAlloca(builder, format.underlying, "allocnum")
    override def underlying(using ts: TypeSupplier): LLVMTypeRef = format.underlying
case class BooleanTypeSpec() extends TypeSpec(TypeTag.Boolean):
    override def alloca(using builder: LLVMBuilderRef, ts: TypeSupplier): LLVMValueRef = 
        LLVMBuildAlloca(builder, ts.i1, "allocbool")
    override def underlying(using ts: TypeSupplier): LLVMTypeRef = ts.i8
case class StringTypeSpec(length: LLVMValueRef) extends TypeSpec(TypeTag.String):
    override def alloca(using builder: LLVMBuilderRef, ts: TypeSupplier): LLVMValueRef = 
        LLVMBuildArrayAlloca(builder, ts.i8, length, "allocstr")
    override def underlying(using ts: TypeSupplier): LLVMTypeRef = ts.string
case class ArrayTypeSpec(length: LLVMValueRef) extends TypeSpec(TypeTag.Array):
    override def alloca(using builder: LLVMBuilderRef, ts: TypeSupplier): LLVMValueRef = 
        LLVMBuildArrayAlloca(builder, ts.arrayItem, length, "allocarray")
    override def underlying(using ts: TypeSupplier): LLVMTypeRef = ts.array

object NumberTypeSpec:
    def forTypeRef(ref: LLVMTypeRef)(using ts: TypeSupplier) =
        val format = LLVMGetTypeKind(ref) match
            case LLVMIntegerTypeKind => LLVMGetIntTypeWidth(ref) match
                case 8 => NumberFormat.byte
                case 32 => NumberFormat.i32
                case 64 => NumberFormat.i64
                case i => throw Error(s"Unknown integer width $i!")
            case LLVMDoubleTypeKind => NumberFormat.double
            case i => throw Error(s"Invalid type ref $i!")
        NumberTypeSpec(format)

final case class TypedValueRef(ty: TypeSpec, value: LLVMValueRef)
class TypeSupplier(context: LLVMContextRef, module: LLVMModuleRef):
    val i32 = LLVMInt32TypeInContext(context)
    val i64 = LLVMInt64TypeInContext(context)
    val double = LLVMDoubleTypeInContext(context)
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