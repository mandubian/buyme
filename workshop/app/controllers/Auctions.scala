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
import play.autosource.core.AfterAction

import reactivemongo.bson.BSONObjectID

import org.mandubian.actorroom._
import models._

object Auctions extends ReactiveMongoAutoSourceController[Auction] {
  lazy val coll = storage.auctions

  def addOffer(idAuction: String) = Action.async(parse.json) { implicit request =>
    request.body.validate[Offer].fold(
      errors => Future(BadRequest(JsError.toFlatJson(errors))),
      offer => this.res.get(BSONObjectID(idAuction)).flatMap {
        case Some((auction, id)) => {
          val updatedAuction = auction.copy( offers = auction.offers:+(offer) )

          services.AuctionGlobal.room.foreach{
            _.supervisor ! Broadcast(
              offer.buyer,
              Json.obj(
                "id" -> Json.toJson(id), 
                "kind" -> "offer", 
                "action" -> "new",
                "value" -> (Json.obj("id" -> idAuction) ++ Json.toJson(updatedAuction).as[JsObject])
              )
            )
          }

          this.res.update(id, updatedAuction).map( unit => Ok(Json.toJson(updatedAuction)) )
        }
        case None => Future(NotFound)
      }
    )
  }

  override def insert = Action.async(parse.json){ request =>
    Json.fromJson[Auction](request.body).map{ auction =>
      res.insert(auction).map{ id =>
        services.AuctionGlobal.room.foreach{
          _.supervisor ! Broadcast(
            auction.seller,
            Json.obj(
              "kind" -> "auction", 
              "action" -> "new",
              "value" -> (Json.obj("id" -> id.stringify) ++ Json.toJson(auction).as[JsObject])
            )
          )
        }
        Ok(Json.toJson(id))
      }
    }.recoverTotal{ e => Future.successful(BadRequest(JsError.toFlatJson(e))) }
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