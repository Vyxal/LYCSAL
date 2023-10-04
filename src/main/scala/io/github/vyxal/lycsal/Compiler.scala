package io.github.vyxal.lycsal

import vyxal.parsing.Lexer
import vyxal.Parser
import vyxal.Context
import vyxal.AST
import org.bytedeco.javacpp.*
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*
import scala.collection.mutable.Stack
import scala.annotation.tailrec
import vyxal.StringHelpers

class Compiler():
    private val context = LLVMContextCreate()
    private val module = LLVMModuleCreateWithNameInContext("vyxal", context)
    private val builder = LLVMCreateBuilderInContext(context)
    private val ts = TypeSupplier(context, module)
    private val literals = Literals(ts)
    private val util = RuntimeFunctions(context, module, builder, ts)
    private val elements = Elements(builder, ts, util)

    @tailrec
    final def generateIf(conditions: List[AST], bodies: List[AST], elseBody: Option[AST], returnBlockIn: Option[LLVMBasicBlockRef] = None)(using scope: Scope): Unit = 
        given LLVMBuilderRef = builder
        given LLVMContextRef = context
        given TypeSupplier = ts
        _compile(conditions.head)
        val thenBlock = LLVMAppendBasicBlockInContext(context, scope.function, "then")
        val elifBlock = LLVMAppendBasicBlockInContext(context, scope.function, "elif")
        val returnBlock = returnBlockIn match
            case Some(b) => b
            case None => LLVMCreateBasicBlockInContext(context, "ret")
        LLVMPositionBuilderAtEnd(builder, LLVMGetPreviousBasicBlock(thenBlock))
        val targetVal = i1ify(scope.pointers.popLoadWithType())(using ts)
        LLVMBuildCondBr(builder, targetVal, thenBlock, elifBlock)
        LLVMPositionBuilderAtEnd(builder, thenBlock)
        _compile(bodies.head)
        LLVMBuildBr(builder, returnBlock)
        LLVMPositionBuilderAtEnd(builder, elifBlock)
        if bodies.tail.isEmpty then
            elseBody.foreach(_compile)
            LLVMBuildBr(builder, returnBlock)
            LLVMAppendExistingBasicBlock(scope.function, returnBlock)
            LLVMPositionBuilderAtEnd(builder, returnBlock)
        else
            generateIf(conditions.tail, bodies.tail, elseBody, Some(returnBlock))

    def generateWhile(conditionIn: Option[AST], body: AST)(using scope: Scope) =
        given LLVMBuilderRef = builder
        given TypeSupplier = ts
        scope.withCtxVar(0, BooleanTypeSpec())((ctxVar1) =>
            scope.withCtxVar(1, NumberTypeSpec(NumberFormat.i64))((ctxVar2) => 
                val loopEntryBlock = LLVMAppendBasicBlockInContext(context, scope.function, "loopentry")
                val loopBodyBlock = LLVMAppendBasicBlockInContext(context, scope.function, "loopbody")
                val loopExitBlock = LLVMAppendBasicBlockInContext(context, scope.function, "loopexit")
                LLVMBuildBr(builder, loopEntryBlock)
                LLVMPositionBuilderAtEnd(builder, loopEntryBlock)
                conditionIn match
                    case Some(condition) =>
                        _compile(condition)
                        val condVal = i1ify(scope.pointers.popLoadWithType())(using ts)
                        ctxVar1.set(condVal)
                        LLVMBuildCondBr(builder, condVal, loopBodyBlock, loopExitBlock)
                    case None =>
                        ctxVar1.set(LLVMConstInt(ts.i1, 1, 0))
                        LLVMBuildBr(builder, loopBodyBlock)
                LLVMPositionBuilderAtEnd(builder, loopBodyBlock)
                _compile(body)
                ctxVar2.set(LLVMBuildAdd(builder, ctxVar2.get.value, LLVMConstInt(ts.i64, 1, 0), "increment-counter"))
                LLVMBuildBr(builder, loopEntryBlock)
                LLVMPositionBuilderAtEnd(builder, loopExitBlock)
            )
        )

    def generateTernary(thenBody: AST, elseBody: Option[AST])(using scope: Scope) =
        given LLVMBuilderRef = builder
        given TypeSupplier = ts
        val condition = i1ify(scope.pointers.popLoadWithType())(using ts)
        val thenBlock = LLVMAppendBasicBlockInContext(context, scope.function, "then")
        val elseBlock = LLVMAppendBasicBlockInContext(context, scope.function, "else")
        val continueBlock = LLVMAppendBasicBlockInContext(context, scope.function, "continue")
        LLVMBuildCondBr(builder, condition, thenBlock, if elseBody.isDefined then elseBlock else continueBlock)
        LLVMPositionBuilderAtEnd(builder, thenBlock)
        _compile(thenBody)
        LLVMBuildBr(builder, continueBlock)
        elseBody.foreach((ast) => {
            LLVMPositionBuilderAtEnd(builder, elseBlock)
            _compile(ast)
            LLVMBuildBr(builder, continueBlock)
        })
        LLVMPositionBuilderAtEnd(builder, continueBlock)
        
        

    def compile(ast: AST): LLVMModuleRef = 
        val mainFunctionType = LLVMFunctionType(ts.i32, new PointerPointer(), 0, 0)
        val mainFunction = LLVMAddFunction(module, "main", mainFunctionType)
        LLVMSetFunctionCallConv(mainFunction, LLVMCCallConv)
        val mainBlock = LLVMAppendBasicBlockInContext(context, mainFunction, "entry")
        LLVMPositionBuilderAtEnd(builder, mainBlock)
        given primaryScope: Scope = Scope.root(mainFunction)
        _compile(ast)
        LLVMBuildRet(builder, LLVMConstInt(ts.i32, 0, 0))
        module

    def _compile(ast: AST)(using scope: Scope): Unit = 
        given PointerStack = scope.pointers
        given TypeSupplier = ts
        given LLVMContextRef = context
        given LLVMBuilderRef = builder
        ast match
            case AST.Number(value, _) => literals.generateNumber(value)
            case AST.Lst(elems, _) => literals.generateArray(elems.map((elem) =>
                given childScope: Scope = scope.child
                _compile(elem)
                childScope.pointers.popLoadWithType()
            ))
            case AST.Str(value, _) => literals.generateString(value)
            case AST.DictionaryString(value, _) => literals.generateString(StringHelpers.decompress(value))

            case AST.Command(cmd, _) => elements.elements.get(cmd) match
                case Some(element) => element.compile().foreach(scope.pointers.pushStore)
                case None => throw Error(s"Unknown symbol $cmd")
            case AST.Group(elems, _, _) => elems.foreach(_compile)

            case AST.IfStatement(conds, bodies, elseBody, _) => generateIf(conds, bodies, elseBody)
            case AST.While(cond, body, _) => generateWhile(cond, body)(using scope.child)
            case AST.Ternary(thenBody, elseBody, _) => generateTernary(thenBody, elseBody)

            case _ => throw Error(s"Unsupported AST type $ast")
    end _compile