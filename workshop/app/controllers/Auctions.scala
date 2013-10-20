package controllers

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import play.api._
import play.api.mvc._
import play.api.Play.current

import play.api.libs.json._
import play.api.libs.functional.syntax._

import play.modules.reactivemongo.json.collection.JSONCollection
import play.modules.reactivemongo.json.BSONFormats._
import play.autosource.reactivemongo._

import reactivemongo.bson.BSONObjectID

import org.mandubian.actorroom._
import models._


class CustomReactiveMongoAutoSource(coll: JSONCollection) extends ReactiveMongoAutoSource[Auction](coll: JSONCollection) {
  override def insert(auction: Auction)(implicit ctx: scala.concurrent.ExecutionContext): Future[BSONObjectID] = super.insert(auction) map { id =>
    services.AuctionGlobal.room.foreach{ room =>
      room.supervisor ! Broadcast(
        auction.seller,
        Json.obj(
          "kind" -> "auction", 
          "action" -> "new",
          "value" -> (Json.obj("id" -> id.stringify) ++ Json.toJson(auction).as[JsObject])
        )
      )
    }
    id
  }
}

object Auctions extends ReactiveMongoAutoSourceController[Auction] {
  lazy val coll = storage.auctions

  override lazy val res = new CustomReactiveMongoAutoSource(coll)

  def addOffer(idAuction: String) = Action.async(parse.json) { implicit request =>
    request.body.validate[Offer].fold(
      errors => Future(BadRequest(JsError.toFlatJson(errors))),
      offer => this.res.get(BSONObjectID(idAuction)).flatMap {
        case Some((auction, id)) => {
          val updatedAuction = auction.copy( offers = auction.offers:+(offer) )

          this.res.update(id, updatedAuction).map { _ =>
            services.AuctionGlobal.room.foreach{ room =>
              room.supervisor ! Broadcast(
                offer.buyer,
                Json.obj(
                  "id" -> Json.toJson(id), 
                  "kind" -> "offer", 
                  "action" -> "new",
                  "value" -> (Json.obj("id" -> idAuction) ++ Json.toJson(updatedAuction).as[JsObject])
                )
              )
            }

            Ok(Json.toJson(updatedAuction)) 
          }
        }
        case None => Future(NotFound)
      }
    )
  }

}

// VIEWS
package auctions {
  object templates extends Controller {
    def create = Action {
      Ok(views.html.auctions.create())
    }
  }
}