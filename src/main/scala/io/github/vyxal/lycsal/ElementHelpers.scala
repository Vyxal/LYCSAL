package io.github.vyxal.lycsal

import org.bytedeco.llvm.LLVM.LLVMBuilderRef

// This file is roughly equivalent to Vyxal's Functions.scala

// These signatures are different from their Vyxal ones, since the "context" is supplied by the Elements class
type Monad = TypedValueRef => Option[TypedValueRef]
type Dyad = (TypedValueRef, TypedValueRef) => Option[TypedValueRef]
type Triad = (TypedValueRef, TypedValueRef, TypedValueRef) => Option[TypedValueRef]
type Tetrad = (TypedValueRef, TypedValueRef, TypedValueRef, TypedValueRef) => Option[TypedValueRef]

// This is also different; it directly takes the PointerStack and returns any number of TypedValueRefs
type DirectFn = () => PointerStack ?=> List[TypedValueRef]

sealed abstract class Implementer[F](val arity: Int):
    def directify(impl: F)(using builder: LLVMBuilderRef): DirectFn

// These four are pretty much the same thing right now

object Monad extends Implementer[Monad](1):
    override def directify(impl: Monad)(using builder: LLVMBuilderRef): DirectFn = 
        () => pointers ?=> impl(pointers.popLoadWithType()).toList

object Dyad extends Implementer[Dyad](2):
    override def directify(impl: Dyad)(using builder: LLVMBuilderRef): DirectFn = 
        () => pointers ?=> impl(pointers.popLoadWithType(), pointers.popLoadWithType()).toList

object Triad extends Implementer[Triad](3):
    override def directify(impl: Triad)(using builder: LLVMBuilderRef): DirectFn = 
        () => pointers ?=> impl(pointers.popLoadWithType(), pointers.popLoadWithType(), pointers.popLoadWithType()).toList

object Tetrad extends Implementer[Tetrad](4):
    override def directify(impl: Tetrad)(using builder: LLVMBuilderRef): DirectFn = 
        () => pointers ?=> impl(pointers.popLoadWithType(), pointers.popLoadWithType(), pointers.popLoadWithType(), pointers.popLoadWithType()).toList