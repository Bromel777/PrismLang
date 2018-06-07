package org.encryfoundation.prismlang.core

sealed trait PValue {

  val tpe: Types.PType
  val value: tpe.Underlying

  override def equals(obj: scala.Any): Boolean = obj match {
    case that: PValue => that.value == this.value
    case _ => false
  }
}

object ESValue {

  def apply(n: String, t: Types.PType)(v: Any): PValue = new PValue {
    override val tpe: Types.PType = t
    override val value: tpe.Underlying = v.asInstanceOf[tpe.Underlying]
  }
}
