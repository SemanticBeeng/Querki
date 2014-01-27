package querki.spaces

import models.{Attachment, Collection, Kind, OID, Property, PType, PTypeBuilder, Thing, ThingState}
import models.Thing.PropMap

import querki.ecology.EcologyMember
import querki.time.{DateTime, epoch}
import querki.util.QLog
import querki.values.SpaceState
  
// It really feels like there should be *some* way to pass getThingStream as a parameter without introducing this
// artificial trait. But so far, I haven't found the syntax to pass a function that takes type parameters as
// a function parameter, so we'll hack it for now:
trait ThingStreamLoader {
  def getThingStream[T <: Thing](kind:Int)(state:SpaceState)(builder:(OID, OID, PropMap, DateTime) => T):Stream[T]
}

/**
 * Performs the guts of loading a Space. This is broken out from Load itself specifically so that it can be
 * decoupled from the actual SQL code, stubbed and unit-tested.
 */
trait SpaceLoader { self:EcologyMember =>
  
  // Fields defined in SpacePersister or the test stub:
  def Core:querki.core.Core
  def SystemInterface:querki.system.System
  def SpacePersistence:querki.spaces.SpacePersistence
  def UserAccess:querki.identity.UserAccess
  
  def id:OID
  def name:String
  def owner:OID
  
  def doLoad(loader:ThingStreamLoader):SpaceState = {
      
      // Start off using the App to boot this Space. Then we add each aspect as we read it in.
      // This works decently for now, but will fall afoul when we try to have local meta-Properties;
      // those will wind up with pointer errors.
      // TODO: Do the Property load in multiple phases, so we can have local meta-properties.
      // TODO: this should use the App, not SystemSpace:
      var curState:SpaceState = SystemInterface.State
      
      def getThings[T <: Thing](kind:Int)(builder:(OID, OID, PropMap, DateTime) => T):Map[OID, T] = {
        val tStream = loader.getThingStream(kind)(curState)(builder)
        (Map.empty[OID, T] /: tStream) { (m, t) =>
          try {
            m + (t.id -> t)
          } catch {
            case error:Exception => {
              QLog.error("Error while trying to assemble ThingStream " + id, error)
              m
            }
          }
        }
      }
      
      val spaceStream = loader.getThingStream(Kind.Space)(curState) { (thingId, modelId, propMap, modTime) =>
        new SpaceState(
             thingId,
             modelId,
             () => propMap,
             owner,
             name,
             modTime,
             Some(SystemInterface.State),
             // TODO: dynamic PTypes
             Map.empty[OID, PType[_]],
             Map.empty[OID, Property[_,_]],
             Map.empty[OID, ThingState],
             // TODO (probably rather later): dynamic Collections
             Map.empty[OID, Collection],
             None,
             ecology
            )
      }
      
      curState =
        if (spaceStream.isEmpty) {
          // This wants to be a Big Nasty Error!
          QLog.error("Was unable to find/load Space " + id + "/" + name + ". INVESTIGATE THIS!")
          
          // In the meantime, we fall back on a plain Space Thing:
          new SpaceState(
            id,
            SystemInterface.State.id,
            Core.toProps(
              Core.setName(name),
              interface[querki.basic.Basic].DisplayTextProp("We were unable to load " + name + " properly. An error has been logged; our apologies.")
              ),
            owner,
            name,
            epoch,
            Some(SystemInterface.State),
            Map.empty[OID, PType[_]],
            Map.empty[OID, Property[_,_]],
            Map.empty[OID, ThingState],
            // TODO (probably rather later): dynamic Collections
            Map.empty[OID, Collection],
            None,
            ecology
            )
        } else
          spaceStream.head
      
      val loadedProps = getThings(Kind.Property) { (thingId, modelId, propMap, modTime) =>
        val typ = SystemInterface.State.typ(Core.TypeProp.first(propMap))
        // This cast is slightly weird, but safe and should be necessary. But I'm not sure
        // that the PTypeBuilder part is correct -- we may need to get the RT correct.
//        val boundTyp = typ.asInstanceOf[PType[typ.valType] with PTypeBuilder[typ.valType, Any]]
        val boundTyp = typ.asInstanceOf[PType[Any] with PTypeBuilder[Any, Any]]
        val coll = SystemInterface.State.coll(Core.CollectionProp.first(propMap))
        // TODO: this feels wrong. coll.implType should be good enough, since it is viewable
        // as Iterable[ElemValue] by definition, but I can't figure out how to make that work.
        val boundColl = coll.asInstanceOf[Collection]
        new Property(thingId, id, modelId, boundTyp, boundColl, () => propMap, modTime)
      }
      curState = curState.copy(spaceProps = loadedProps)
      
      val things = getThings(Kind.Thing) { (thingId, modelId, propMap, modTime) =>
        new ThingState(thingId, id, modelId, () => propMap, modTime)        
      }
      
      val attachments = getThings(Kind.Attachment) { (thingId, modelId, propMap, modTime) =>
        new Attachment(thingId, id, modelId, () => propMap, modTime)        
      }
      
      val allThings = things ++ attachments
      curState = curState.copy(things = allThings)
      
      // Now we do a second pass, to resolve anything left unresolved:
      def secondPassProps[T <: Thing](thing:T)(copier:(T, PropMap) => T):T = {
        val fixedProps = thing.props.map { propPair =>
          val (id, value) = propPair
          value match {
            case unres:UnresolvedPropValue => {
              val propOpt = curState.prop(id)
              val v = propOpt match {
                case Some(prop) => prop.deserialize(value.firstTyped(SpacePersistence.UnresolvedPropType).get)
                case None => value
              }
              (id, v)
            }
            case _ => propPair
          }
        }
        copier(thing, fixedProps)
      }

      curState = secondPassProps(curState)((state, props) => state.copy(pf = () => props))
      
      val fixedAllProps = curState.spaceProps.map{ propPair =>
        val (id, prop) = propPair
        (id, secondPassProps(prop)((p, metaProps) => p.copy(pf = () => metaProps)))
      }.toSeq
      curState = curState.copy(spaceProps = Map(fixedAllProps:_*))
      
      // BLOCKING, but useful: make the owner visible, so that we can, eg, write URLs
      curState = curState.copy(ownerIdentity = UserAccess.getIdentity(owner))
          
      curState
  }
  
}