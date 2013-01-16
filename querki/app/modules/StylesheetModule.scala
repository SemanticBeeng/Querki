package modules.stylesheet

import play.api.templates.Html

import models._
import models.Space.oidMap
import models.system._
// We need this because some of these objects were originally created in System,
// and their OIDs live there:
import models.system.OIDs.sysId
import models.Thing._

// This is for the PageEventManager and related classes:
import controllers._

object OIDs {
  val StylesheetOID = sysId(25)
  val CSSTextOID = sysId(27)
  val CSSOID = sysId(28)
  val StylesheetBaseOID = sysId(29)
  val GoogleFontOID = sysId(32)
}

class StylesheetModule(val moduleId:Short) extends modules.Module {

  val props = oidMap[Property[_,_,_]](
    StylesheetProp,
    CSSProp,
    GoogleFontProp
  )
  
  val things = oidMap[ThingState](StylesheetBase)
  
  val types = oidMap[PType[_]](CSSTextType)
  
  override def addSystemObjects(state:SpaceState):SpaceState = {
    state.copy(
      spaceProps = props ++: state.spaceProps, 
      things = things ++: state.things,
      types = types ++: state.types)
  }
  
  object HeaderHandler extends Contributor[HtmlEvent, String] {
    def notify(evt:HtmlEvent, sender:Aggregator[HtmlEvent,String]):String = {
      // For the time being, only Thing pages show styles. This will probably change
      if (evt.template == QuerkiTemplate.Thing) {
    	val rc = evt.rc
    	val thingOpt = rc.thing
    	if (rc.state.isDefined && thingOpt.isDefined && thingOpt.get.hasProp(StylesheetProp)(rc.state.get)) {
    	  implicit val state = rc.state.get
    	  val thing = thingOpt.get
    	  val result = new StringBuilder("")
    	  // TODO: there should be a common pattern for dereferencing a Link like this:
    	  val stylesheetOID = thing.first(StylesheetProp)
    	  val stylesheet = state.thing(stylesheetOID)
    	  if (stylesheet.hasProp(GoogleFontProp)) {
    	    val fonts = stylesheet.first(GoogleFontProp).raw.toString
    	    result ++= "<link rel=\"stylesheet\" type=\"text/css\" href=\"http://fonts.googleapis.com/css?family=" + fonts + "\">"
    	  }
    	  result ++= "<style type=\"text/css\">" + stylesheet.first(CSSProp) + "</style>"
    	  result.toString
    	} else {
    	  ""
    	}
      } else {
        ""
      }
    }
  }
  
  override def init = {
    PageEventManager.addHeaders.subscribe(HeaderHandler)
  }
  
  override def term = {
    PageEventManager.addHeaders.unsubscribe(HeaderHandler)
  }
}

import models.system.OIDs._
import OIDs._
  
/**
 * A Type for CSS Text as a proper Property, so we can edit directly in Querki.
 */
class CSSTextType(tid:OID) extends SystemType[String](tid,
    toProps(
        setName("Type-CSS"))
    ) with SimplePTypeBuilder[String]
{
  // TODO: filter any Javascript-enabling keywords! This should go in doFromUser().
    
  def doDeserialize(v:String) = v
  def doSerialize(v:String) = v
  def doRender(v:String) = Wikitext(v)
    
  val doDefault = ""
    
  override def renderInput(prop:Property[_,_,_], state:SpaceState, currentValue:Option[String]):Html =
    CommonInputRenderers.renderLargeText(prop, state, currentValue)
}
object CSSTextType extends CSSTextType(CSSTextOID)
  
/**
 * Points to the optional CSS file for this Thing. If placed on a Space, applies Space-wide.
 * 
 * TBD: this isn't quite so "system-ish". We might start defining properties nearer to their
 * relevant functionality.
 */
object StylesheetProp extends SystemProperty(StylesheetOID, LinkType, Optional,
    toProps(
      setName("Stylesheet"),
      LinkModelProp(StylesheetBase),
      AppliesToKindProp(Kind.Thing)
      ))

/**
 * This special property is used for Stylesheet Things. Basically, if this Thing is used as
 * the Stylesheet for other Things, it should have this Property set.
 */
object CSSProp extends SystemProperty(CSSOID, CSSTextType, Optional,
    toProps(
      setName("CSS")
      ))

/**
 * This is the name of a Google Font to embed. It should be referenced from a Stylesheet.
 * 
 * TODO: this probably shouldn't be TextType, but some more limited type that only allows
 * a small character set.
 * 
 * TODO: this should probably be a List instead of just a single item, so you can specify
 * multiple fonts.
 */
object GoogleFontProp extends SystemProperty(GoogleFontOID, PlainTextType, Optional,
    toProps(
      setName("Google Font Name"),
      // TODO: in fact, this only applies to Stylesheets:
      AppliesToKindProp(Kind.Thing)
      ))

object StylesheetBase extends ThingState(StylesheetBaseOID, systemOID, RootOID,
    toProps(
      setName("Stylesheet"),
      IsModelProp(true),
      DisplayTextProp("""
This is a Stylesheet. It is intended for more advanced users, and allows you to controls the look of your Things.
          
The important Property here is CSS -- this contains normal CSS code, just like for a webpage.
"""),
      CSSProp("")))