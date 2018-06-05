package org.encryfoundation.prismlang.compiler

import org.encryfoundation.prismlang.compiler.scope.ScopedSymbolTable
import org.encryfoundation.prismlang.core.{TypeSystem, Types}
import org.encryfoundation.prismlang.core.Ast._
import org.encryfoundation.prismlang.compiler.scope.Symbol

import scala.util.Try

case class StaticAnalyser(types: TypeSystem) {

  import StaticAnalyser._

  var scopes: List[ScopedSymbolTable] = List(ScopedSymbolTable.initial)

  def scanContract(contract: Expr.Contract): Try[Expr.Contract] = Try {
    val args: List[(String, Types.PType)] = resolveArgs(contract.args)
    args.foreach(p => currentScope.insert(Symbol(p._1, p._2)))
    val bodyS: Expr = scan(contract.body)
    matchType(contract.body.tpe, Types.PBoolean)
    contract.copy(bodyS)
  }

  /** Scan each node according to the specific rule, then
    * compute its type (if the node is untyped by default)
    * and return modified copy of the node */
  def scan: Scan =
    scanLet orElse
      scanDef orElse
      scanLambda orElse
      scanIf orElse
      pass

  def scanLet: Scan = {
    /** Scan the value to be assigned to the const, compute
      * its type, compare it with the declared one (if declared)
      * and add name to the scope. */
    case let @ Expr.Let(name, value, typeIdentOpt) =>
      val valueS: Expr = scan(value)
      val valueType: Types.PType = value.tpe
      typeIdentOpt.foreach(t => matchType(valueType, resolveType(t)))
      addToScope(name, valueType)
      let.copy(name, valueS, typeIdentOpt)
  }

  def scanDef: Scan = {
    /** Resolve arguments and return type, insert function symbol
      * to the current scope, create scope for the function body
      * with argument inserted, scan body and compare its type
      * with the declared one, pop function scope. */
    case func @ Expr.Def(ident, args, body, returnTypeIdent) =>
      val declaredReturnType: Types.PType = resolveType(returnTypeIdent)
      val params: List[(String, Types.PType)] = resolveArgs(args)
      currentScope.insert(Symbol(ident.name, Types.PFunc(params, declaredReturnType)))
      val bodyScope: ScopedSymbolTable = ScopedSymbolTable.nested(currentScope, isFunc = true)
      params.foreach(p => bodyScope.insert(Symbol(p._1, p._2)))
      scopes = bodyScope :: scopes
      val bodyS: Expr = scan(body)
      matchType(declaredReturnType, body.tpe)
      scopes = scopes.tail
      func.copy(ident, args, bodyS, returnTypeIdent)
  }

  def scanLambda: Scan = {
    /** Resolve arguments, create scope for the function body
      * with argument inserted, scan body, pop function scope. */
    case lamb @ Expr.Lambda(args, body, _) =>
      val params: List[(String, Types.PType)] = resolveArgs(args)
      val bodyScope: ScopedSymbolTable = ScopedSymbolTable.nested(currentScope, isFunc = true)
      params.foreach(p => bodyScope.insert(Symbol(p._1, p._2)))
      scopes = bodyScope :: scopes
      val bodyS: Expr = scan(body)
      scopes = scopes.tail
      lamb.copy(args, bodyS, computeType(lamb))
  }

  def scanIf: Scan = {
    /** Scan test expression ensuring its type is `Bool`,
      * then scan bodies of each branch. */
    case ifExp @ Expr.If(test, body, orelse, _) =>
      scan(test)
      matchType(test.tpe, Types.PBoolean)
      val bodyScope: ScopedSymbolTable = ScopedSymbolTable.nested(currentScope)
      scopes = bodyScope :: scopes
      val bodyS: Expr = scan(body)
      scopes = scopes.tail
      val elseScope: ScopedSymbolTable = ScopedSymbolTable.nested(currentScope)
      scopes = elseScope :: scopes
      val orelseS: Expr = scan(orelse)
      scopes = scopes.tail
      ifExp.copy(test, bodyS, orelseS, computeType(ifExp))
    /** Scan the target to be assigned, ensure that its type
      * can be cast to the declared local type, then scan
      * bodies of each branch. */
    case letIf @ Expr.IfLet(local, typeIdent, target, body, orelse, _) =>
      val targetS: Expr = scan(target)
      val localT: Types.PType = resolveType(typeIdent)
      if (localT.isSubtypeOf(target.tpe)) error(s"${target.tpe} can not be cast to $localT")
      val bodyScope: ScopedSymbolTable = ScopedSymbolTable.nested(currentScope)
      bodyScope.insert(Symbol(local.name, localT))
      scopes = bodyScope :: scopes
      val bodyS: Expr = scan(body)
      scopes = scopes.tail
      val elseScope: ScopedSymbolTable = ScopedSymbolTable.nested(currentScope)
      scopes = elseScope :: scopes
      val orelseS: Expr = scan(orelse)
      scopes = scopes.tail
      letIf.copy(local, typeIdent, targetS, bodyS, orelseS, computeType(letIf))
  }

  def pass: Scan = {
    case any => any
  }

  def computeType(expr: Expr): Types.PType = if (!expr.tpe.isNit) expr.tpe else expr match {
    case Expr.Name(Ident(name), _) => currentScope.lookup(name).map(_.tpe).getOrElse(error(s"$name is undefined"))
    case Expr.Attribute(value, attr, _) => computeType(value) match {
      case prod: Types.PProduct => prod.getAttrType(attr.name).getOrElse(error(s"${attr.name} is not defined in ${prod.ident}"))
      case other => error(s"${other.ident} is not an object")
    }
    case Expr.If(_, body, orelse, _) => primaryType(body.tpe, orelse.tpe)
    case Expr.IfLet(_, _, _, body, orelse, _) => primaryType(body.tpe, orelse.tpe)
  }

  def currentScope: ScopedSymbolTable = scopes.head

  /** Resolves the type from its string representation
    * (including type parameters) */
  def resolveType(ident: TypeIdent): Types.PType = {
    val typeParams: List[Types.PType] = ident.typeParams.map(p => types.typeByIdent(p)
      .getOrElse(error(s"Type '$p' is undefined.")))
    types.typeByIdent(ident.name).map {
      case Types.PArray(_) =>
        if (typeParams.size == 1) Types.PArray(typeParams.head)
        else error("'Array[T]' takes exactly one type parameter")
      case Types.POption(_) =>
        if (typeParams.size == 1) Types.POption(typeParams.head)
        else error("'Option[T]' takes exactly one type parameter")
      case otherT: Types.PType =>
        if (typeParams.isEmpty) otherT
        else error(s"'$otherT' does not take type parameters")
    }.getOrElse(error(s"Type '${ident.name}' is undefined."))
  }

  def resolveArgs(args: List[(Ident, TypeIdent)]): List[(String, Types.PType)] =
    args.map { case (id, typeId) => id.name -> resolveType(typeId) }

  def addToScope(ident: Ident, tpe: Types.PType): Unit =
    currentScope.insert(Symbol(ident.name, tpe))

  def matchType(required: Types.PType, actual: Types.PType): Unit =
    if (!(required == actual || actual.isSubtypeOf(required))) error(s"Type mismatch: $required != $actual")

  // TODO: Find common type between two Products.
  def primaryType(t1: Types.PType, t2: Types.PType): Types.PType = {
    if (t1 == t2) t1
    else if (t2.isSubtypeOf(t1)) t1
    else if (t1.isSubtypeOf(t2)) t2
    else (t1, t2) match {
      case (prod1: Types.PProduct, prod2: Types.PProduct) => Types.PAny
      case (_, _) => Types.PAny
    }
  }

  def error(msg: String) = throw new SemanticAnalysisException(msg)
}

object StaticAnalyser {

  type Scan = PartialFunction[Expr, Expr]
}
