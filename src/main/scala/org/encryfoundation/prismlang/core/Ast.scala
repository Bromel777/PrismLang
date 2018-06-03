package org.encryfoundation.prismlang.core

object Ast {

  import Types._

  sealed trait Node

  sealed trait Expr extends Node { var tpe: PType }
  object Expr {

    case class Contract(body: List[Expr]) extends Expr { override var tpe: PType = PBoolean }

    case class Block(body: List[Expr]) extends Expr { override var tpe: PType = Nit }

    // Syntactical constructions
    case class Let(name: Ident, value: Expr, typeIdentOpt: Option[TypeIdent]) extends Expr { override var tpe: PType = PUnit }

    case class Def(name: Ident, args: List[(Ident, TypeIdent)], body: Expr, returnTypeIdent: TypeIdent) extends Expr { override var tpe: PType = PUnit }

    case class Lambda(args: List[(Ident, TypeIdent)], body: Expr) extends Expr { override var tpe: PType = Nit }

    case class If(test: Expr, body: Expr, orelse: List[Expr]) extends Expr { override var tpe: PType = Nit }

    case class IfLet(name: Ident, typeIdent: TypeIdent, target: Expr) extends Expr { override var tpe: PType = Nit }

    // Operations
    case class Bool(op: BooleanOp, values: List[Expr]) extends Expr { override var tpe: PType = PBoolean }

    case class Bin(left: Expr, op: Operator, right: Expr, override var tpe: PType = Nit) extends Expr

    case class Unary(op: UnaryOp, operand: Expr, override var tpe: PType = Nit) extends Expr

    /** Sequences are required for compare to distinguish between: x < 4 < 3 and (x < 4) < 3 */
    case class Compare(left: Expr, ops: List[Ast.CompOp], comparators: List[Expr]) extends Expr { override var tpe: PType = PBoolean }

    // Refs
    case class Name(ident: Ident) extends Expr { override var tpe: PType = Nit }

    case class Call(func: Expr, args: List[Expr]) extends Expr { override var tpe: PType = Nit }

    case class Attribute(value: Expr, attr: Ident, override var tpe: PType = Nit) extends Expr

    case class Subscript(value: Expr, slice: SliceOp, override var tpe: PType = Nit) extends Expr

    // Constants
    case class IntConst(value: Long) extends Expr { override var tpe: PType = PInt }

    case class Str(value: String) extends Expr { override var tpe: PType = PString }

    case class Base58Str(value: String) extends Expr { override var tpe: PType = PByteVector }

    case class Base16Str(value: String) extends Expr { override var tpe: PType = PByteVector }

    case object True extends Expr { override var tpe: PType = PBoolean }

    case object False extends Expr { override var tpe: PType = PBoolean }

    // Transformers
    case class SizeOf(coll: Expr) extends Expr { override var tpe: PType = Nit }

    case class Exists(coll: Expr, predicate: Expr) extends Expr { override var tpe: PType = Nit }

    case class Sum(coll: Expr, override var tpe: PType) extends Expr

    case class Map(coll: Expr, func: Expr, override var tpe: PType) extends Expr
  }

  sealed trait SliceOp
  object SliceOp {

    case object Ellipsis extends SliceOp

    case class Slice(lower: Option[Expr], upper: Option[Expr], step: Option[Expr]) extends SliceOp

    case class ExtSlice(dims: List[SliceOp]) extends SliceOp

    case class Index(value: Expr) extends SliceOp
  }

  sealed trait BooleanOp
  object BooleanOp {

    case object And extends BooleanOp

    case object Or extends BooleanOp
  }

  sealed trait Operator
  case object Operator {

    case object Add extends Operator

    case object Sub extends Operator

    case object Mult  extends Operator

    case object Div  extends Operator

    case object Mod extends Operator

    case object Pow extends Operator
  }

  sealed trait UnaryOp
  object UnaryOp {

    case object Invert extends UnaryOp

    case object Not extends UnaryOp

    case object UAdd extends UnaryOp

    case object USub extends UnaryOp
  }

  sealed trait CompOp
  object CompOp {

    case object Eq extends CompOp

    case object NotEq extends CompOp

    case object Lt extends CompOp

    case object LtE extends CompOp

    case object Gt extends CompOp

    case object GtE extends CompOp

    case object Is extends CompOp

    case object IsNot extends CompOp

    case object In extends CompOp

    case object NotIn extends CompOp
  }

  case class Ident(name: String)

  case class TypeIdent(name: String, typeParams: List[String])
}