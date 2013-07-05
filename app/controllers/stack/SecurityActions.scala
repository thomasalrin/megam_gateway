/* 
** Copyright [2012-2013] [Megam Systems]
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
package controllers.stack

import scalaz._
import scalaz.Validation._
import Scalaz._
import play.api.mvc.Action
import play.api.Logger
import play.api.mvc.RequestHeader
import play.api.mvc.Request
import play.api.mvc.AnyContent
import play.api.mvc.Result
import play.api.mvc.RawBuffer
import play.api.mvc.Codec
import play.api.http.Status._
import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac
import org.apache.commons.codec.binary.Base64
import jp.t2v.lab.play2.stackc.{ RequestWithAttributes, RequestAttributeKey, StackableController }

import com.github.nscala_time.time.Imports._
import controllers.stack.stack._
import controllers.funnel._
import models.{ Accounts }

/**
 * @author rajthilak
 *
 */

object SecurityActions {

  val X_Megam_EMAIL = "X-Megam-EMAIL"
  val X_Megam_APIKEY = "X-Megam-APIKEY"
  val X_Megam_DATE = "X-Megam-DATE"
  val X_Megam_HMAC = "X-Megam-HMAC"

  val Content_Type = "Content-Type"
  val application_json = "application/json"
  val Accept = "Accept"
  val application_vnd_megam_json = "application/vnd.megam+json"

  def Authenticated[A](req: FunnelRequestBuilder[A]): ValidationNel[ResultInError, RawResult] = {
    Logger.debug(("%-20s -->[%s]").format("SecurityActions", "Authenticated:Entry"))
    req.funneled match {
      case Success(succ) => {
        Logger.debug(("%-20s -->[%s]").format("FUNNLEDREQ-S", succ.toString))
        (succ map (x => bazookaAtDataSource(x))).getOrElse(
          Validation.failure[ResultInError, RawResult](ResultInError(Tuple2(BAD_REQUEST,
            """Autorization failure for 'email:' Invalid content in header. API server couldn't parse it.:'
            |
            |Please verify your email and api_key  combination. This  needs to  appear  as-is  during onboarding
            |from the megam.co webiste. This is a bug in the API client. If you have accessed   this   using our
            |api code (megam_api for ruby, scala, java etc..) then PLEASE LOG A JIRA ISSUE.""".stripMargin + "\n "))).toValidationNel)
      }
      case Failure(err) =>
        Logger.debug(("%-20s -->[%s]").format("FUNNLEDREQ-F", err.getMessage))
        Validation.failure[ResultInError, RawResult](ResultInError(Tuple2(BAD_REQUEST,
          """Autorization failure for 'email:' Invalid content in header. API server couldn't parse it.:'
            |
            |Please verify your email and api_key  combination. This  needs to  appear  as-is  during onboarding
            |from the megam.co webiste. This is a bug in the API client. If you have accessed   this   using our
            |api code (megam_api for ruby, scala, java etc..) then PLEASE LOG A JIRA ISSUE.""".stripMargin + "\n "))).toValidationNel

    }
  }

  /**
   * This Authenticated function will extract information from the request and calculate
   * an HMAC value. The request is parsed as tolerant text, as content type is application/json,
   * which isn't picked up by the default body parsers in the controller.
   * If the header exists then
   * the string is split on : and the header is parsed
   * else
   */
  def bazookaAtDataSource(freq: FunneledRequest): ValidationNel[ResultInError, RawResult] = {
    play.api.Logger.debug("<---------------------------------------->")
    Accounts.findByEmail(freq.maybeEmail.get) match {
      case Success(optAcc) => {
        Logger.debug(("%-20s -->[%s]").format("Option Account", optAcc))
        val foundAccount = optAcc.get
        val calculatedHMAC = GoofyCrypto.calculateHMAC(foundAccount.api_key, freq.mkSign)

        Logger.debug("A :%-20s B :%-20s C :%-20s".format(calculatedHMAC, foundAccount.api_key, freq.clientAPIHmac.get))

        if (calculatedHMAC === freq.clientAPIHmac.get) {
          Validation.success[ResultInError, RawResult](RawResult(1, Map[String, String](
            "id" -> foundAccount.id,
            "api_key" -> foundAccount.email,
            "created_at" -> DateTime.now.toString))).toValidationNel
        } else {
          Validation.failure[ResultInError, RawResult](ResultInError(Tuple2(UNAUTHORIZED,
            """Authorization failure for 'email:' HMAC doesn't match: '%s'
            |
            |Please verify your email and api_key  combination. This  needs to  appear  as-is  during onboarding
            |from the megam.co webiste. If this error persits, ask for help on the forums.""".format(foundAccount.email).stripMargin + "\n "))).toValidationNel
        }
      }
      case Failure(err) => {
        Validation.failure[ResultInError, RawResult](ResultInError(Tuple2(FORBIDDEN,
          """Autorization failure for 'email:' Couldn't locate the 'email':'
            |
            |Please verify your email and api_key  combination. This  needs to  appear  as-is  during onboarding
            |from the megam.co webiste. If this error persits, ask for help on the forums.""".stripMargin + "\n "))).toValidationNel
      }
    }
  }
}

object GoofyCrypto {
  /**
   * Calculate the MD5 hash for the specified content (UTF-16 encoded)
   */
  def calculateMD5(content: Option[String]): Option[String] = {
    val MD5 = "MD5"
    Logger.debug(("%-20s -->[%s]").format("body content", content))
    val digest = MessageDigest.getInstance(MD5)
    digest.update(content.getOrElse(throw new IllegalArgumentException(
      """Body wasn't found for this API call.  No content in body. API server couldn't parse it.'
            |
            |Please verify your information that is sent in the body. This  needs to  appear  as-is  during onboarding
            |from the megam.co webiste. This is a bug in the API client. If you have accessed   this   using our
            |api code (megam_api for ruby, scala, java etc..) then PLEASE LOG A JIRA ISSUE.""".stripMargin + "\n ")).getBytes())
    new String(Base64.encodeBase64(digest.digest())).some
  }

  /**
   * Calculate the HMAC for the specified data and the supplied secret (UTF-16 encoded)
   */
  def calculateHMAC(secret: String, toEncode: String): String = {
    val HMACSHA1 = "HmacSHA1"
    Logger.debug(("%-20s -->[%s]").format("raw hmac entry", toEncode))

    val signingKey = new SecretKeySpec(secret.getBytes(), "RAW")
    val mac = Mac.getInstance(HMACSHA1)
    mac.init(signingKey)
    val rawHmac = mac.doFinal(toEncode.getBytes())
    val hmacAsByt = dumpByt(rawHmac.some)
    Logger.debug(("%-20s -->[%s] = %s").format("raw hmac", rawHmac, hmacAsByt))
    hmacAsByt
  }

  def dumpByt(bytesOpt: Option[Array[Byte]]): String = {
    val b: Array[String] = (bytesOpt match {
      case Some(bytes) => bytes.map(byt => (("00" + (byt &
        0XFF).toHexString)).takeRight(2))
      case None => Array(0X00.toHexString)
    })
    b.mkString("")
  }

}

