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
    Map("account" -> "00414d9f-c95b-45c2-99a2-614536e08441"),
    Map("account" -> "00689a84-1e87-492b-8174-496cf99eb300"),
    Map("account" -> "007a3bca-c3c6-407f-83a8-28793fe586f8"),
    Map("account" -> "00841eb6-1528-4720-a9fb-3b43b8757528"),
    Map("account" -> "009beba0-9e1f-4ae7-a17e-6f86b7fa988d"),
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
    Map("task" -> "01HCFT0KK2EGMSZQ1XZYGTT0E8"),
    Map("task" -> "01HCFT0S1ZYNNN1SG1XJKMS0F2"),
    Map("task" -> "01HCFT0YC1GTPY33ZH1PYDN3Z6"),
    Map("task" -> "01HCFT13W60899TP78EN36TS8T"),
    Map("task" -> "01HCFT195H8XSE1HAX6Q93PFEP"),
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
