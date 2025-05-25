package gatling

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder

import java.util.UUID
import scala.concurrent.duration._

class ClouddriverTest extends Simulation {

  private val title = "clouddriver-test"

  private val postCreateAccountRequest = "POST create-account"
  private val getAccountHistoryRequest = "GET account-history"
  private val getAccountsByTypeRequest = "GET accounts-by-type"
  private val delAccountRequest = "DEL delete-account"

  private val postCreateTaskRequest = "POST create-task (hotspot)"
  private val getOneTaskRequest = "GET one-task"
  private val getTaskOwnerRequest = "GET task-owner"
  private val patchRetryTaskRequest = "PATCH retry-task (hotspot)"

  private val circularAccountValues = Array(
    Map("account" -> "092fe289-857f-408c-a64d-eef8c3bbc86b"),
    Map("account" -> "0b87d50e-0170-4fde-8b37-8b86aa050f40"),
    Map("account" -> "16cf7e19-526f-43a0-b42c-4e70417bdec2"),
    Map("account" -> "d9c301ff-7a4d-46d7-9471-4c065f3e1c3b"),
    Map("account" -> "fc68523b-4634-43f4-b865-2dd804008e92"),
  ).circular

  private val postCreateAccount =
    http(postCreateAccountRequest)
      .post("/credentials")
      .header("Content-Type", "application/json")
      .body(StringBody(
        """
          |{
          |    "type": "#{provider}",
          |    "name": "#{uuid}",
          |    "permissions":{"READ":["my-group"],"WRITE":["my-group"]},
          |    "context": "eks",
          |    "namespaces": ["default"]
          |}
        """.stripMargin))
      .check(
        jsonPath("$.name").saveAs("account"),
        status.is(200)
      )

  private val getAccountHistory =
    http(getAccountHistoryRequest)
      .get("/credentials/#{account}/history")
      .check(status.is(200))

  private val getAccountsByType =
    http(getAccountsByTypeRequest)
      .get("/credentials/type/#{provider}")
      .check(status.is(200))

  private val delAccount =
    http(delAccountRequest)
      .delete("/credentials/#{account}")
      .check(status.is(200))

  private val circularTaskValues = Array(
    Map("task" -> "01JW2CXXCPFJ8VK4HH1GJE4SB3"),
    Map("task" -> "01JW2CXY9D8APDK1Q653TS757D"),
    Map("task" -> "01JW2CXZ0AYX11QPA8W00GM51Z"),
    Map("task" -> "01JW2CXZR3TQ1ZMF2RXKJBHRNY"),
    Map("task" -> "01JW2CY0FJQ76E1P55RKSHH9PN"),
  ).circular

  private val postCreateTask =
    http(postCreateTaskRequest)
      .post("/#{provider}/ops")
      .header("Content-Type", "application/json")
      .body(StringBody(
        """
          | [{}]
        """.stripMargin))
      .check(status.is(200))

  private val getOneTask =
    http(getOneTaskRequest)
      .get("/task/#{task}")
      .check(status.is(200))

  private val getTaskOwner =
    http(getTaskOwnerRequest)
      .get("/#{provider}/task/#{task}/owner")
      .check(status.is(200))

  private val circularRetryValues = Array(
    Map("retry" -> "true"),
    Map("retry" -> "false")
  ).circular

  private val patchRetryTask =
    http(patchRetryTaskRequest)
      .patch("/#{provider}/task/#{task}")
      .header("Content-Type", "application/json")
      .body(StringBody(
        """
          |{
          |    "retry": "#{retry}"
          |}
        """.stripMargin))
      .check(status.is(200))

  private val clouddriver: ScenarioBuilder = scenario(title)
    .group("Account") {
      exec(session => {
        session.set("provider", "kubernetes")
      })
        .exec(session => {
          val uuid: String = UUID.randomUUID().toString
          session.set("uuid", uuid)
        })
        .feed(circularAccountValues)
        .exec(postCreateAccount)
        //.pause(1)
        .exec(getAccountHistory)
        .exec(getAccountsByType)
        .exec(delAccount)
    }
    .group("Task") {
      exec(session => {
        session.set("provider", "kubernetes")
      })
        .feed(circularTaskValues)
        .exec(postCreateTask)
        //.pause(1)
        .exec(getOneTask)
        .exec(getTaskOwner)
        .feed(circularRetryValues)
        .exec(patchRetryTask)
    }

  val protocolReqres: HttpProtocolBuilder = http
    .baseUrl("http://127.0.0.1:7002")
    .disableCaching
  setUp(
    clouddriver
      .inject(
        rampUsers(120) during (60 seconds),
        constantUsersPerSec(8) during (540 seconds)
      )
  ).protocols(protocolReqres)
    .assertions(
      global.successfulRequests.percent.gte(99)
    )
}

// sbt clean compile
// sbt "gatling:testOnly gatling.ClouddriverTest"

//1) no-both (create/retry) -> fast
//2) no-create-task
//3) no-retry-task
//4) baseline/all -> slow
