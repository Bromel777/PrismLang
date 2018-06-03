package org.encryfoundation.prismlang.compiler.scope

import org.encryfoundation.prismlang.compiler.AlreadyDefinedException

import scala.collection.immutable.TreeMap

case class ScopedSymbolTable(scopeLevel: Int,
                             parentalScopeOpt: Option[ScopedSymbolTable] = None,
                             isFunc: Boolean = false) {

  var symbols: TreeMap[String, Symbol] = TreeMap(PredefinedScope.members.map(m => m.name -> m):_*)

  def insert(sym: Symbol): Unit = {
    symbols.get(sym.name).map(_ => throw AlreadyDefinedException(sym.name))
    symbols = symbols.updated(sym.name, sym)
  }

  def lookup(name: String, currentScopeOnly: Boolean = false): Option[Symbol] = symbols.get(name) match {
    case Some(r) => Some(r)
    case None if !currentScopeOnly => parentalScopeOpt.flatMap(_.lookup(name))
    case _ => None
  }

  override def toString: String = s"L$scopeLevel ${this.symbols}"
}

object ScopedSymbolTable {

  def initial: ScopedSymbolTable = ScopedSymbolTable(scopeLevel=1)

  def nested(oldScope: ScopedSymbolTable, isFunc: Boolean = false): ScopedSymbolTable =
    ScopedSymbolTable( oldScope.scopeLevel + 1, Some(oldScope), isFunc)
}
