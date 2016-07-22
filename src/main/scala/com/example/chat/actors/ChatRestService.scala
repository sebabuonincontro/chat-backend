package com.example.chat.actors

import akka.actor.{Props, ActorSystem, Actor}
import akka.pattern.ask
import com.example.chat.actors.chatMessages.{CallChat, CreateChat}
import com.example.chat.services.ChatServices._
import com.example.chat.{ChatWithMessages, Chat, Message, User}
import spray.http.StatusCodes

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object chatMessages{
  case class CreateRoom(user: User)
  case class MessageAdded(message: Message)
  case class CallChat(chatId: Int)
  case class ViewChat(messages: List[Message])
  case class CreateChat(chat: Chat)
}

class ChatActor(implicit val ex: ExecutionContext) extends Actor {

  import akka.pattern.pipe

  override def receive: Receive = {
    case CallChat(id) => refreshChat(id) pipeTo sender
    case CreateChat(chat) => createChat(chat) pipeTo sender
  }
}

class ChatRestService extends HttpActor {

  val system = ActorSystem("chatSystem")
  val chatMailBox = system.actorOf(Props(new ChatActor()), name = "chatActor")

  val chatUrl = "chat"

  def refresh =
    path(chatUrl / IntNumber){ id =>
      get {
        logger.info("call chat: " + id)
        onComplete((chatMailBox ? CallChat(id)).mapTo[Option[ChatWithMessages]]) {
          case Success(value) => { value match {
              case Some(chat) => complete(StatusCodes.OK, chat)
              case None => complete(StatusCodes.NotFound)
            }
          }
          case Failure(error) => {
            logger.error("Error: ", error)
            complete(StatusCodes.InternalServerError, error)
          }
        }
      }
    }

  def createChat =
    path(chatUrl){
      post{
        entity(as[Chat]) { chat =>
          onComplete((chatMailBox ? CreateChat(chat)).mapTo[Chat]) {
            case Success(newChat) => complete(StatusCodes.Created, newChat)
            case Failure(error) => {
              logger.error("Error: ", error)
              complete(StatusCodes.InternalServerError, error)
            }
          }
        }
      }
    }

  override def route = refresh ~ createChat
}