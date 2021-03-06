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
package test

import org.specs2.mutable._
import org.specs2.Specification
import java.net.URL
import org.specs2.matcher.MatchResult
import org.specs2.execute.{ Result => SpecsResult }
import com.stackmob.newman.response.{ HttpResponse, HttpResponseCode }
import com.stackmob.newman._
import com.stackmob.newman.dsl._
import models._
/**
 * @author ram
 *
 */
class BoltDefnsSpec extends Specification {

  def is =
    "BoltDefnsSpec".title ^ end ^ """
  BoltDefnsSpec is the implementation that calls the megam_play API server with the /requests url
  """ ^ end ^
      "The Client Should" ^
      //"Correctly do POST Boltdefns with a valid userid and api key" ! Post.succeeds ^
      //"Correctly do GET  (node name)Boltdefns with a invalid Node name" ! findByInvalidName.succeeds ^
      //"Correctly do GET  (node name)Boltdefns with a valid node name" ! findByName.succeeds ^
      "Correctly do POST Boltdefns with a valid userid and api key" ! Update.succeeds ^
      end

  /**
   * Change the body content in method bodyToStick
   */
  case object Post extends Context {

    protected override def urlSuffix: String = "boltdefns/content"

    protected override def bodyToStick: Option[String] = {      
      val contentToEncode = "{\"node_name\":\"appsample1.megam.co\",\"boltdefns\":{\"username\":\"rr\",\"apikey\":\"dfgythgf\", \"store_name\":\"dbname\",\"url\":\"url\",\"prime\":\"prime\",\"timetokill\":\"timetokill\",\"metered\":\"metered\", \"logging\":\"logging\",\"runtime_exec\":\"runtime_exec\",\"env_sh\":\"env_sh\"}}"      
      Some(new String(contentToEncode))
    }
    protected override def headersOpt: Option[Map[String, String]] = None

    private val post = POST(url)(httpClient)
      .addHeaders(headers)
      .addBody(body)

    def succeeds: SpecsResult = {
      val resp = execute(post)
      resp.code must beTheSameResponseCodeAs(HttpResponseCode.Created)
    }
  }

  case object findByInvalidName extends Context {
    protected override def urlSuffix: String = "boltdefns/checksample"

    protected def headersOpt: Option[Map[String, String]] = None

    private val get = GET(url)(httpClient)
      .addHeaders(headers)
    def succeeds = {
      val resp = execute(get)
      resp.code must beTheSameResponseCodeAs(HttpResponseCode.Ok)
    }
  }
  case object findByName extends Context {
    protected override def urlSuffix: String = "boltdefns/checktest4.megam.co"

    protected def headersOpt: Option[Map[String, String]] = None

    private val get = GET(url)(httpClient)
      .addHeaders(headers)
    def succeeds = {
      val resp = execute(get)
      resp.code must beTheSameResponseCodeAs(HttpResponseCode.Ok)
    }
  }
  
  case object Update extends Context {

    protected override def urlSuffix: String = "boltdefns/update"

    protected override def bodyToStick: Option[String] = {      

      val contentToEncode = "{\"boltdefn_id\":\"BDF456048363326930944\",\"node_name\":\"appsample1.megam.co\",\"runtime_exec\":\"\",\"env_sh\":\"dfghdfgfdkgj\"}"
      Some(new String(contentToEncode))

    }
    protected override def headersOpt: Option[Map[String, String]] = None    
  private val post = POST(url)(httpClient)
      .addHeaders(headers)
      .addBody(body)

    def succeeds: SpecsResult = {
      val resp = execute(post)
      resp.code must beTheSameResponseCodeAs(HttpResponseCode.Created)
    }
  }

}