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
package models.json.tosca

import scalaz._
import scalaz.NonEmptyList._
import scalaz.Validation
import scalaz.Validation._
import Scalaz._
import net.liftweb.json._
import net.liftweb.json.scalaz.JsonScalaz._
import java.util.Date
import java.nio.charset.Charset
import controllers.funnel.FunnelErrors._
import controllers.Constants._
import controllers.funnel.SerializationBase
import models.tosca.{ AssemblyResult, Components }

/**
 * @author ram
 *
 */
class AssemblyResultSerialization(charset: Charset = UTF8Charset) extends SerializationBase[AssemblyResult] {

  protected val JSONClazKey = controllers.Constants.JSON_CLAZ
  protected val IdKey = "id"
  protected val NameKey = "name"
  protected val ComponentsKey = "components"
  protected val PoliciesKey = "policies"
  protected val InputsKey = "inputs"
  protected val OperationsKey = "operations"
  protected val CreatedAtKey ="created_at" 
    
  override implicit val writer = new JSONW[AssemblyResult] {

    import ComponentsSerialization.{ writer => ComponentsWriter }
    
    override def write(h: AssemblyResult): JValue = {
      JObject(
        JField(IdKey, toJSON(h.id)) ::
        JField(JSONClazKey, toJSON("Megam::Assembly")) ::
          JField(NameKey, toJSON(h.name)) ::
          JField(ComponentsKey, toJSON(h.components)(ComponentsWriter)) ::
          JField(PoliciesKey, toJSON(h.policies)) ::
          JField(InputsKey, toJSON(h.inputs)) ::
          JField(OperationsKey, toJSON(h.operations)) ::
          JField(CreatedAtKey, toJSON(h.created_at)) :: Nil)
    }
  }

  override implicit val reader = new JSONR[AssemblyResult] {
    
    import ComponentsSerialization.{ reader => ComponentsReader }

    override def read(json: JValue): Result[AssemblyResult] = {
      val idField = field[String](IdKey)(json)
      val nameField = field[String](NameKey)(json)
      val componentsField = field[Components](ComponentsKey)(json)(ComponentsReader)
      val policiesField = field[String](PoliciesKey)(json)
      val inputsField = field[String](InputsKey)(json)  
      val operationsField = field[String](OperationsKey)(json)
      val createdAtField = field[String](CreatedAtKey)(json)

      (idField |@| nameField |@| componentsField |@| policiesField |@| inputsField |@| operationsField |@| createdAtField) {
        (id: String, name: String, components: Components, policies: String, inputs: String, operations: String, created_at: String) =>
          new AssemblyResult(id, name, components, policies, inputs, operations, created_at)
      }
    }
  }
}