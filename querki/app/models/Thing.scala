package models

import play.api._

import models.system.SystemSpace
import models.system.SystemSpace._

/**
 * Enumeration of what sort of Thing this is. Note that this is an intentionally
 * exclusive set. That's mostly to make it reasonably easy to reason about stuff:
 * if something is a Type, that means it isn't ordinary.
 */
object Kind {
  type Kind = Int
  
  val Thing = 0
  val Type = 1
  val Property = 2
  val Space = 3
  val Collection = 4
}

object Thing {
  type PropMap = Map[ThingPtr, PropValue[_]]
  type PropFetcher = () => PropMap
  
  // A couple of convenience methods for the hard-coded Things in System:
  def toProps(pairs:(ThingPtr,PropValue[_])*):PropFetcher = () => {
    (Map.empty[ThingPtr, PropValue[_]] /: pairs) { (m:Map[ThingPtr, PropValue[_]], pair:(ThingPtr, PropValue[_])) =>
      m + (pair._1 -> pair._2)
    }
  }
  
  // NOTE: don't try to make this more concise -- it causes chicken-and-egg problems in system
  // initialization:
  def setName(str:String):(OID,PropValue[_]) = 
    (NameOID -> PropValue(OneColl(ElemValue(str))))

  // TODO: this escape/unescape is certainly too simplistic to cope with recursive types.
  // Come back to this sometime before we make the type system more robust.
  def escape(str:String) = {
    str.replace("\\", "\\\\").replace(";", "\\;").replace(":", "\\:").replace("}", "\\}")
  }
  def unescape(str:String) = {
    str.replace("\\}", "}").replace("\\:", ":").replace("\\;", ";").replace("\\\\", "\\")
  }
    
  def deserializeProps(str:String, space:SpaceState):PropMap = {
    // Strip the surrounding {} pair:
    val stripExt = str.slice(1, str.length() - 1)
    val propStrs = stripExt.split(";")
    val propPairs = propStrs.map { propStr =>
      val (idStr, valStrAndColon) = propStr.splitAt(propStr.indexOf(':'))
      val valStr = unescape(valStrAndColon.drop(1))
      val id = OID(idStr)
      val prop = space.prop(id)
      val v = prop.deserialize(valStr)
      (prop, v)
    }
    toProps(propPairs:_*)()
  }
}

import Thing._

/**
 * The root concept of the entire world. Thing is the Querki equivalent of Object,
 * the basis of the entire type system.
 * 
 * TODO: note that we thread the whole thing with OIDs, to make it easier to build the
 * whole potentially-immutable stack. Down the line, we might add a second pass that
 * re-threads these things with hard references, to make them faster to process. This
 * should do to start, though.
 */
abstract class Thing(
    val id:OID, 
    val spaceId:ThingPtr, 
    val model:ThingPtr, 
    val kind:Kind.Kind,
    val propFetcher: PropFetcher) extends ThingPtr
{
  lazy val props:PropMap = propFetcher()
  
  def displayName = getProp(NameProp).render.raw
  
  def space:SpaceState = {
    // TODO: do this for real!
    SystemSpace.State
  }
  
  def getModel:Thing = {
    // TODO: this will eventually need to be able to walk the Space-inheritance tree
    model match {
      case directPtr:Thing => directPtr
      case id:OID => space.thing(id)
    }
  }
  
  /**
   * The Property as defined on *this* specific Thing.
   */
  def localProp(propId:ThingPtr):Option[PropAndVal[_, _]] = {
    val (pid,ptr) = propId match {
      case i:OID => (i, space.prop(i))
      case t:Property[_,_] => (t.id, t)
    }
    val byId = props.get(pid)
    if (byId.nonEmpty)
      Some(ptr.pair(byId.get))
    else
      props.get(ptr).map(v => ptr.pair(v))
  }
  
  /**
   * The key method for fetching a Property Value from a Thing. This walks the tree
   * as necessary.
   * 
   * Note that this walks up the tree recursively. It eventually ends with UrThing,
   * which does things a little differently.
   */
  def getProp(propId:ThingPtr):PropAndVal[_,_] = {
    localProp(propId).getOrElse(getModel.getProp(propId))
  }
  
  /**
   * Returns true iff this Thing or any ancestor has the specified property defined on it.
   * Note that this ignores defaults.
   */
  def hasProp(propId:ThingPtr):Boolean = {
    props.contains(propId) || getModel.hasProp(propId)
  }
  
  /**
   * Convenience method -- returns either the value of the specified property or None.
   */
  def getPropOpt(propId:ThingPtr):Option[PropAndVal[_,_]] = {
    if (hasProp(propId))
      Some(getProp(propId))
    else
      None
  }
  
  def renderProps:Wikitext = {
    val listMap = props.map { entry =>
      val prop = space.prop(entry._1)
      val pv = prop.pair(entry._2)
      "<dt>" + prop.displayName + "</dt><dd>" + pv.render.raw + "</dd>"
    }
    Wikitext(listMap.mkString("<dl>", "", "</dl>"))    
  }
  
  /**
   * Every Thing can be rendered -- this returns a Wikitext string that will then be
   * displayed in-page.
   * 
   * TODO: allow this to be redefined with a QL Property if desired.
   */
  def render:Wikitext = {
    val opt = getPropOpt(DisplayTextProp)
    opt.map(pv => pv.render).getOrElse(renderProps)
  }
  
  def serializeProps = {
    val serializedProps = props.map { pair =>
      val (ptr, v) = pair
      val prop = space.prop(ptr)
      val oid = prop.id
      oid.toString + 
        ":" + 
        Thing.escape(prop.serialize(prop.castVal(v)))
    }
    
    serializedProps.mkString("{", ";", "}")
  }
  
  def export:String = {
    "{" +
    "id:" + id.toString + ";" +
    "model:" + model.id.toString + ";" +
    "kind:" + kind.toString + ";" +
    "props:" + serializeProps +
    "}"
  }
}

/**
 * A ThingState represents the value of a Thing as of a particular time.
 * It is immutable -- you change the Thing by going to its Space and telling it
 * to make the change.
 * 
 * Note that Models are basically just ordinary Things.
 */
case class ThingState(i:OID, s:ThingPtr, m:ThingPtr, pf: PropFetcher) 
  extends Thing(i, s, m, Kind.Thing, pf) {}