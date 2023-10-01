package io.github.vyxal.lycsal

import vyxal.parsing.Lexer
import vyxal.Parser
import vyxal.Context
import vyxal.AST

import org.bytedeco.javacpp.*
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*
import scala.collection.mutable.Stack
import java.io.File
import java.nio.ByteBuffer
import scala.sys.process.Process
import java.io.InputStream
import java.io.StringBufferInputStream

object LYCSAL:
    val vyxalVersion = vyxal.Interpreter.getClass().getPackage().getImplementationVersion()
    def createAst(code: String)(using ctx: Context): AST = 
        val lexRes = Lexer(code)
        val tokens = lexRes match
          case Right(tokens) => tokens
          case Left(err) => throw Error(s"Lexing failed: $err")

        val parsed = Parser.parse(tokens)
        parsed match
            case Right(ast) => ast
            case Left(error) => throw Error(s"Error while parsing $code: $error")
    end createAst

    def compile(code: String): LLVMModuleRef = 
        given vyCtx: Context = Context()
        val ast = createAst(code)
        LLVMInitializeNativeAsmParser()
        LLVMInitializeNativeAsmPrinter()
        LLVMInitializeNativeTarget()
        val compiler = new Compiler()
        val module = compiler.compile(ast)

        val error = BytePointer()
        if (LLVMVerifyModule(module, LLVMPrintMessageAction, error) != 0) then
            throw ValidationException(error.getString())
        module

    def optimize(module: LLVMModuleRef) =
        val manager = LLVMCreatePassManager()
        LLVMAddPromoteMemoryToRegisterPass(manager)
        LLVMAddCFGSimplificationPass(manager)
        LLVMAddNewGVNPass(manager)
        LLVMRunPassManager(manager, module)
        ()
    
    def stringify(module: LLVMModuleRef) =
        LLVMPrintModuleToString(module).getString()

    def link(module: LLVMModuleRef, filename: String, clangPathIn: Option[File] = None) =
        val clangPath = clangPathIn match
            case Some(file) => file.getCanonicalPath()
            case None =>
                scribe.warn("Using embedded Clang executable. This mode voids your warranty!")
                scribe.warn("(not that you ever had one, of course)")
                Loader.load(org.bytedeco.llvm.program.clang().getClass)
        Process(clangPath, "-x" :: "ir" ::  "-o" :: filename :: "-" :: Nil).#<(StringBufferInputStream(LLVMPrintModuleToString(module).getString())).! match
            case 0 => ()
            case exit: Int => throw SubprocessExitExeption(clangPath, exit)
        
