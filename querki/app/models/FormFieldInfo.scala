package models

/**
 * This class is the result of parsing a field out of a Play HTTP Form. It crosses the
 * HTTP/internal API lines somewhat uncomfortably; not clear yet where it belongs.
 */
case class FormFieldInfo(prop:Property[_,_], value:Option[PropValue], empty:Boolean, isValid:Boolean) {
  def propId = prop.id
  def isEmpty = empty || value.isEmpty
}