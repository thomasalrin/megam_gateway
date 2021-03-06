/* Copyright [2013-2014] [Megam Systems]
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
 * @author subash
 *
 */
class NodesSpec extends Specification {

  def is =
    "NodesSpec".title ^ end ^ """
  NodesSpec is the implementation that calls the megam_play API server with the /nodes url
  """ ^ end ^
      "The Client Should" ^
      "Correctly do POST requests APP  n EC2 with a valid userid and api key" ! PostApp.succeeds ^
      //"Correctly do POST requests BOLT   EC2 with a valid userid and api key" ! PostBolt.succeeds ^
      //"Correctly do POST requests with an invalid body" ! PostInvalidBody.succeeds ^
      //"Correctly do GET  (emai)requests with a valid userid and api key" ! findByEmail.succeeds ^
      //"Correctly do GET  (appsample1.megam.co) requests with an valid Node name" ! findByNameApp.succeeds ^
      //"Correctly do GET  (boltsample1.megam.co) requests with a valid Node name" ! findByNameBolt.succeeds ^
      //"Correctly do GET  (appfail1.megam.co) requests with an Invalid Node name" ! findByInvalidName.succeeds ^
      //"Correctly do UPDATE requests APP  n EC2 with a valid userid and api key" ! UpdateApp.succeeds ^
  end

  /**
   * Change the body content in method bodyToStick
   */
  case object PostApp extends Context {

    protected override def urlSuffix: String = "nodes/content"

    protected override def bodyToStick: Option[String] = {
      val command = new NodeCommand(new NodeSystemProvider(NodeProvider.empty),
        new NodeCompute("ec2", new NodeComputeDetail("default", "106719", "100", ""),
          new NodeComputeAccess("megam_hp", "ubuntu", " ~/.ssh/megam_hp.pem", "https://s3-ap-southeast-1.amazonaws.com/cloudkeys/sandy@megamsandbox.com/default", "", "", "region")),
        new NodeCloudToolService(new NodeCloudToolChef("knife", "hp server create -c", "java", "-N appsample.megam.co"))).json

      val contentToEncode = "{\"node_name\":\"appsample.megam.co\",\"node_type\":\"APP\",\"noofinstances\":1,\"req_type\":\"CREATE\",\"command\":" +
        command + ",\"predefs\":{\"name\":\"rails\",\"scm\":\"scm\", \"war\":\"some.war\",\"db\":\"db\", \"queue\":\"queue\"}," +
        "\"appdefns\":{\"timetokill\":\"timetokill\",\"metered\":\"metered\", \"logging\":\"logging\",\"runtime_exec\":\"runtime_exec\",\"env_sh\":\"env_sh\"}," +
        "\"boltdefns\":{\"username\":\"\",\"apikey\":\"\", \"store_name\":\"\",\"url\":\"\",\"prime\":\"\",\"timetokill\":\"\",\"metered\":\"\", \"logging\":\"\",\"runtime_exec\":\"\",\"env_sh\":\"\"},\"appreq\":{},\"boltreq\":{}}"
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

  case object PostBolt extends Context {

    protected override def urlSuffix: String = "nodes/content"

    protected override def bodyToStick: Option[String] = {
      val command = new NodeCommand(new NodeSystemProvider(NodeProvider.empty),
        new NodeCompute("ec2", new NodeComputeDetail("default", "106719", "100", "tenant"),
          new NodeComputeAccess("megam_hp", "ubuntu", " ~/.ssh/megam_hp.pem", "https://s3-ap-southeast-1.amazonaws.com/cloudkeys/sandy@megamsandbox.com/default", "", "", "region")),
        new NodeCloudToolService(new NodeCloudToolChef("knife", "hp server create", "java", "-N boltsample.megam.co"))).json

      val contentToEncode = "{\"node_name\":\"boltsample.megam.co\",\"node_type\":\"BOLT\",\"noofinstances\":1,\"req_type\":\"CREATE\",\"command\":" +
        command + ",\"predefs\":{\"name\":\"rails\",\"scm\":\"scm\", \"war\":\"some.war\",\"db\":\"db\", \"queue\":\"queue\"}," +
        "\"appdefns\":{\"timetokill\":\"\",\"metered\":\"\", \"logging\":\"\",\"runtime_exec\":\"\",\"env_sh\":\"\"}," +
        "\"boltdefns\":{\"username\":\"rr\",\"apikey\":\"dfgythgf\", \"store_name\":\"dbname\",\"url\":\"url\",\"prime\":\"prime\",\"timetokill\":\"timetokill\",\"metered\":\"metered\", \"logging\":\"logging\",\"runtime_exec\":\"runtime_exec\",\"env_sh\":\"env_sh\"},\"appreq\":{},\"boltreq\":{}}"
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

  case object PostInvalidBody extends Context {

    protected override def urlSuffix: String = "nodes/content"

    protected override def bodyToStick: Option[String] = {
      val contentToEncode = "{\"node_name\":\"atlas.megam.co\",\"command\":\"commands\",\"predefs\":{\"name\":\"rails\",\"scm\":\"scm\", \"db\":\"db\", \"queue\":\"queue\"}}"
      Some(new String(contentToEncode))
    }
    protected override def headersOpt: Option[Map[String, String]] = None

    private val post = POST(url)(httpClient)
      .addHeaders(headers)
      .addBody(body)

    def succeeds: SpecsResult = {
      val resp = execute(post)
      resp.code must beTheSameResponseCodeAs(HttpResponseCode.BadRequest)
    }
  }

  case object findByEmail extends Context {
    protected override def urlSuffix: String = "nodes"

    protected def headersOpt: Option[Map[String, String]] = None

    private val get = GET(url)(httpClient)
      .addHeaders(headers)
    def succeeds = {
      val resp = execute(get)
      resp.code must beTheSameResponseCodeAs(HttpResponseCode.Ok)
    }
  }
  case object findByNameApp extends Context {
    protected override def urlSuffix: String = "nodes/appsample1.megam.co"

    protected def headersOpt: Option[Map[String, String]] = None

    private val get = GET(url)(httpClient)
      .addHeaders(headers)
    def succeeds = {
      val resp = execute(get)
      resp.code must beTheSameResponseCodeAs(HttpResponseCode.Ok)
    }
  }

  case object findByNameBolt extends Context {
    protected override def urlSuffix: String = "nodes/boltsample1.megam.co"

    protected def headersOpt: Option[Map[String, String]] = None

    private val get = GET(url)(httpClient)
      .addHeaders(headers)
    def succeeds = {
      val resp = execute(get)
      resp.code must beTheSameResponseCodeAs(HttpResponseCode.Ok)
    }
  }
  
  case object findByInvalidName extends Context {
    protected override def urlSuffix: String = "nodes/appfail1.megam.co"

    protected def headersOpt: Option[Map[String, String]] = None

    private val get = GET(url)(httpClient)
      .addHeaders(headers)
    def succeeds = {
      val resp = execute(get)
      resp.code must beTheSameResponseCodeAs(HttpResponseCode.NotFound)
    }
  }
  
  case object UpdateApp extends Context {

    protected override def urlSuffix: String = "nodes/update"

    protected override def bodyToStick: Option[String] = {      

      val contentToEncode = "{\"node_name\":\"change1.megam.co\",\"accounts_id\":\"\",\"status\":\"DELETED\",\"appdefnsid\":\"\",\"boltdefnsid\":\"\",\"new_node_name\":\"text-change1.megam.co\"}"
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