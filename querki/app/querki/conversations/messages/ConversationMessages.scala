package querki.conversations.messages

import models.{OID, ThingId}

import querki.conversations._
import querki.identity.User

/**
 * Note that ConversationMessages are never sent directly on their own; instead, they are wrapped in a
 * ConversationRequest, and routed through the Space. The subclasses of ConversationMessage are the
 * payloads of ConversationRequest.
 */
sealed trait ConversationMessage

/**
 * This should get a ThingConversations as its response.
 */
case class GetConversations(thing:OID) extends ConversationMessage

/**
 * Someone has submitted a new Comment for this Space. This should get an AddedNode in response.
 */
case class NewComment(comment:Comment) extends ConversationMessage

/**
 * Message informing us about a newly-added Comment.
 */
case class AddedNode(parentId:Option[CommentId], node:ConversationNode)