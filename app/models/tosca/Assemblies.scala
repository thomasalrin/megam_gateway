/* 
** Copyright [2013-2014] [Megam Systems]
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
** http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package models.tosca

import scalaz._
import scalaz.syntax.SemigroupOps
import scalaz.NonEmptyList._
import scalaz.effect.IO
import scalaz.Validation._
import scalaz.EitherT._
import Scalaz._
import org.megam.util.Time
import controllers.stack._
import controllers.Constants._
import controllers.funnel.FunnelErrors._
import models.tosca._
import models._
import models.cache._
import com.stackmob.scaliak._
import com.basho.riak.client.query.indexes.{ RiakIndexes, IntIndex, BinIndex }
import com.basho.riak.client.http.util.{ Constants => RiakConstants }
import org.megam.common.riak.{ GSRiak, GunnySack }
import org.megam.common.uid.UID
import net.liftweb.json._
import net.liftweb.json.scalaz.JsonScalaz._
import java.nio.charset.Charset

/**
 * @author rajthilak
 *
 */
case class AssembliesInput(name: String, assemblies: models.tosca.AssembliesList, inputs: AssembliesInputs)

case class AssembliesInputs(id: String, assemblies_type: String, label: String) {
  val json = "{\"id\": \"" + id + "\", \"assemblies_type\":\"" +  assemblies_type + "\",\"label\" : \"" + label +"\"}"  
}

case class AssembliesResult(id: String, name: String, assemblies: models.tosca.AssembliesList, inputs: AssembliesInputs, created_at: String) {

  def toJValue: JValue = {
    import net.liftweb.json.scalaz.JsonScalaz.toJSON
    import models.json.tosca.AssembliesResultSerialization
    val preser = new AssembliesResultSerialization()
    toJSON(this)(preser.writer)
  }

  def toJson(prettyPrint: Boolean = false): String = if (prettyPrint) {
    pretty(render(toJValue))
  } else {
    compactRender(toJValue)
  }
}

object AssembliesResult {

 // def apply = new AssembliesResult(new String(), new String(), new String(), new AssembliesInputs(new String(), new String(), new String()), new String())             
  
  def fromJValue(jValue: JValue)(implicit charset: Charset = UTF8Charset): Result[AssembliesResult] = {
    import net.liftweb.json.scalaz.JsonScalaz.fromJSON
    import models.json.tosca.AssembliesResultSerialization
    val preser = new AssembliesResultSerialization()
    fromJSON(jValue)(preser.reader)
  }

  def fromJson(json: String): Result[AssembliesResult] = (Validation.fromTryCatch {
    parse(json)
  } leftMap { t: Throwable =>
    UncategorizedError(t.getClass.getCanonicalName, t.getMessage, List())
  }).toValidationNel.flatMap { j: JValue => fromJValue(j) }

}

object Assemblies {

  implicit val formats = DefaultFormats
  private def riak: GSRiak = GSRiak(MConfig.riakurl, "assemblies")
 // implicit def CSARsSemigroup: Semigroup[CSARResults] = Semigroup.instance((f1, f2) => f1.append(f2))

  val metadataKey = "ASSEMBLIES"
  val metadataVal = "Assemblies Creation"
  val bindex = BinIndex.named("assemblies")

  /**
   * A private method which chains computation to make GunnySack when provided with an input json, email.
   * parses the json, and converts it to nodeinput, if there is an error during parsing, a MalformedBodyError is sent back.
   * After that flatMap on its success and the account id information is looked up.
   * If the account id is looked up successfully, then yield the GunnySack object.
   */
  private def mkGunnySack(email: String, input: String): ValidationNel[Throwable, Option[GunnySack]] = {
    play.api.Logger.debug(("%-20s -->[%s]").format("tosca.Assemblies", "mkGunnySack:Entry"))
    play.api.Logger.debug(("%-20s -->[%s]").format("email", email))
    play.api.Logger.debug(("%-20s -->[%s]").format("json", input))

    val ripNel: ValidationNel[Throwable, AssembliesInput] = (Validation.fromTryCatch {
      parse(input).extract[AssembliesInput]
    } leftMap { t: Throwable => new MalformedBodyError(input, t.getMessage) }).toValidationNel //capture failure
    
    for {
      rip <- ripNel
      aor <- (Accounts.findByEmail(email) leftMap { t: NonEmptyList[Throwable] => t }) 
      uir <- (UID(MConfig.snowflakeHost, MConfig.snowflakePort, "ams").get leftMap { ut: NonEmptyList[Throwable] => ut })
    } yield {
      val bvalue = Set(aor.get.id)
      val json = new AssembliesResult(uir.get._1 + uir.get._2, rip.name, rip.assemblies, rip.inputs, Time.now.toString).toJson(false)

    //  val json = "{\"id\": \"" + (uir.get._1 + uir.get._2) + "\",\"name\":\"" + rip.name + "\",\"assemblies\":" + rip.assemblies + ",\"inputs\":" + rip.inputs.json + ",\"created_at\":\"" + Time.now.toString + "\"}"
      new GunnySack((uir.get._1 + uir.get._2), json, RiakConstants.CTYPE_TEXT_UTF8, None,
        Map(metadataKey -> metadataVal), Map((bindex, bvalue))).some   
    }
  }

  /*
   * create new market place item with the 'name' of the item provide as input.
   * A index name assemblies name will point to the "csars" bucket
   */
  def create(email: String, input: String): ValidationNel[Throwable, Option[AssembliesResult]] = {
    play.api.Logger.debug(("%-20s -->[%s]").format("tosca.Assemblies", "create:Entry"))
    play.api.Logger.debug(("%-20s -->[%s]").format("email", email))
    play.api.Logger.debug(("%-20s -->[%s]").format("yaml", input))

    (mkGunnySack(email, input) leftMap { err: NonEmptyList[Throwable] =>
      new ServiceUnavailableError(input, (err.list.map(m => m.getMessage)).mkString("\n"))
    }).toValidationNel.flatMap { gs: Option[GunnySack] =>

      (riak.store(gs.get) leftMap { t: NonEmptyList[Throwable] => t }).
        flatMap { maybeGS: Option[GunnySack] =>
          maybeGS match {
            case Some(thatGS) => (parse(thatGS.value).extract[AssembliesResult].some).successNel[Throwable]
            case None => {
              play.api.Logger.warn(("%-20s -->[%s]").format("Assemblies.created success", "Scaliak returned => None. Thats OK."))
              (parse(gs.get.value).extract[AssembliesResult].some).successNel[Throwable];
            }
          }
        }
    }
  }
  
  /*def findLinksByName(csarslinksNameList: Option[List[String]]): ValidationNel[Throwable, CSARLinkResults] = {
    play.api.Logger.debug(("%-20s -->[%s]").format("tosca.CSARs", "findLinksByNodeName:Entry"))
    play.api.Logger.debug(("%-20s -->[%s]").format("csarlinksList", csarslinksNameList))
    (csarslinksNameList map {
      _.map { csarslinksName =>
        InMemory[ValidationNel[Throwable, CSARResults]]({
          cname: String =>
            {
              play.api.Logger.debug("tosca.CSARs findLinksByName: csars:" + csarslinksName)
              (findByName(csarsName) leftMap { t: NonEmptyList[Throwable] =>
                new ServiceUnavailableError(csarsName, (t.list.map(m => m.getMessage)).mkString("\n"))
              }).toValidationNel.flatMap { xso: Option[GunnySack] =>
                xso match {
                  case Some(xs) => {
                    (Validation.fromTryCatch {
                      parse(xs.value).extract[CSARResult]
                    } leftMap { t: Throwable =>
                      new ResourceItemNotFound(csarsName, t.getMessage)
                    }).toValidationNel.flatMap { j: CSARResult =>
                      Validation.success[Throwable, CSARResults](nels(j.some)).toValidationNel //screwy kishore, every element in a list ?
                      //go after CSRLinknde
                    }
                  }
                  case None => Validation.failure[Throwable, CSARResults](new ResourceItemNotFound(csarsName, "")).toValidationNel
                }
              }
            }
        }).get(csarsName).eval(InMemoryCache[ValidationNel[Throwable, CSARResults]]())
      }
    } map {
      _.foldRight((CSARResults.empty).successNel[Throwable])(_ +++ _)
    }).head //return the folded element in the head. 
*/
/*
  def findByName(csarsNameList: Option[List[String]]): ValidationNel[Throwable, CSARResults] = {
    play.api.Logger.debug(("%-20s -->[%s]").format("tosca.CSARs", "findByNodeName:Entry"))
    play.api.Logger.debug(("%-20s -->[%s]").format("csarList", csarsNameList))
    (csarsNameList map {
      _.map { csarsName =>
        InMemory[ValidationNel[Throwable, CSARResults]]({
          cname: String =>
            {
              play.api.Logger.debug("tosca.CSARs findByName: csars:" + csarsName)
              (riak.fetch(csarsName) leftMap { t: NonEmptyList[Throwable] =>
                new ServiceUnavailableError(csarsName, (t.list.map(m => m.getMessage)).mkString("\n"))
              }).toValidationNel.flatMap { xso: Option[GunnySack] =>
                xso match {
                  case Some(xs) => {
                    (Validation.fromTryCatch {
                      parse(xs.value).extract[CSARResult]
                    } leftMap { t: Throwable =>
                      new ResourceItemNotFound(csarsName, t.getMessage)
                    }).toValidationNel.flatMap { j: CSARResult =>
                      Validation.success[Throwable, CSARResults](nels(j.some)).toValidationNel //screwy kishore, every element in a list ? 
                    }
                  }
                  case None => Validation.failure[Throwable, CSARResults](new ResourceItemNotFound(csarsName, "")).toValidationNel
                }
              }
            }
        }).get(csarsName).eval(InMemoryCache[ValidationNel[Throwable, CSARResults]]())
      }
    } map {
      _.foldRight((CSARResults.empty).successNel[Throwable])(_ +++ _)
    }).head //return the folded element in the head. 

  }

  /*
   * An IO wrapped finder using an email. Upon fetching the account_id for an email, 
   * the csarnames are listed on the index (account.id) in bucket `CSARs`.
   * Using a "csarname" as key, return a list of ValidationNel[List[CSARResult]] 
   * Takes an email, and returns a Future[ValidationNel, List[Option[CSARResult]]]
   */
  def findByEmail(email: String): ValidationNel[Throwable, CSARResults] = {
    play.api.Logger.debug(("%-20s -->[%s]").format("tosca.CSARs", "findByEmail:Entry"))
    play.api.Logger.debug(("%-20s -->[%s]").format("email", email))
    val res = eitherT[IO, NonEmptyList[Throwable], ValidationNel[Throwable, CSARResults]] {
      (((for {
        aor <- (Accounts.findByEmail(email) leftMap { t: NonEmptyList[Throwable] => t }) //captures failure on the left side, success on right ie the component before the (<-)
      } yield {
        val bindex = BinIndex.named("")
        val bvalue = Set("")
        new GunnySack("csar", aor.get.id, RiakConstants.CTYPE_TEXT_UTF8,
          None, Map(metadataKey -> metadataVal), Map((bindex, bvalue))).some
      }) leftMap { t: NonEmptyList[Throwable] => t } flatMap {
        gs: Option[GunnySack] => riak.fetchIndexByValue(gs.get)
      } map { nm: List[String] =>
        (if (!nm.isEmpty) findByName(nm.some) else
          new ResourceItemNotFound(email, "CSAR = nothing found.").failureNel[CSARResults])
      }).disjunction).pure[IO]
    }.run.map(_.validation).unsafePerformIO
    res.getOrElse(new ResourceItemNotFound(email, "CSAR = nothing found.").failureNel[CSARResults])
  }

  implicit val sedimentCSARsResults = new Sedimenter[ValidationNel[Throwable, CSARResults]] {
    def sediment(maybeASediment: ValidationNel[Throwable, CSARResults]): Boolean = {
      val notSed = maybeASediment.isSuccess
      play.api.Logger.debug("%-20s -->[%s]".format("|^/^|-->CSR:sediment:", notSed))
      notSed
    }
  }
*/
}