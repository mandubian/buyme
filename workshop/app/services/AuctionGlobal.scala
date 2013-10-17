package services

import scala.concurrent._

import akka.actor._

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.concurrent.Akka
import play.api.mvc._

import org.mandubian.actorroom._

object AuctionGlobal {
  private val roomP = Promise[Room]()
  val room : Future[Room] = roomP.future

  def async[A]( fws: => Future[WebSocket[A]] )(implicit frameFormatter: WebSocket.FrameFormatter[A]) = {
    WebSocket[A](h => (e, i) => {
      fws onSuccess { case ws => ws.f(h)(e, i) }
    })
  }

  def init(implicit app: play.api.Application) {
    val room = Room()
    roomP.success(room)
    Akka.system.actorOf(Props(classOf[AuctionCompletionNotifier], room.supervisor, app))
    play.Logger.info("Application has started")
  }
}