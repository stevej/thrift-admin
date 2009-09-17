/*
 * Copyright 2009 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.service.admin

import java.io._
import java.net.{ServerSocket, Socket}
import java.util.concurrent.CountDownLatch
import com.twitter.json.Json
import com.twitter.stats.Stats
import net.lag.configgy.{Configgy, RuntimeEnvironment}
import net.lag.logging.Logger

class AdminHttpService(server: ServerInterface, runtime: RuntimeEnvironment) {
  val log = Logger.get

  val port = Configgy.config.getInt("admin_http_port", 9990)
  val serverSocket = new ServerSocket(port)
  var startupLatch: CountDownLatch = null

  val thread = new Thread("AdminHttpService") {
    override def run() {
      log.info("Starting admin http service on port %d", port)
      startupLatch.countDown()
      try {
        while (true) {
          val client = serverSocket.accept()
          handleRequest(client)
          try {
            client.close()
          } catch {
            case _ =>
          }
        }
      } catch {
        case e: InterruptedException =>
          // silently die.
        case e: Exception =>
          log.error(e, "AdminHttpService uncaught exception; dying: %s", e.toString)
      }
    }
  }

  def handleRequest(client: Socket) {
    val in = new BufferedReader(new InputStreamReader(client.getInputStream()))
    val requestLine = in.readLine()
    while (in.readLine() != "") { }
    val segments = requestLine.split(" ", 3)
    if (segments.length != 3) {
      sendError(client, "Malformed request line.")
      return
    }
    val command = segments(0).toLowerCase()
    if (command != "get") {
      sendError(client, "Request must be GET.")
      return
    }
    val pathSegments = segments(1).split("/").filter(_.length > 0)
    pathSegments(0) match {
      case "ping" =>
        send(client, "pong")
      case "reload" =>
        send(client, "ok")
        Configgy.reload
      case "shutdown" =>
        send(client, "ok")
        server.shutdown()
        AdminService.stop()
      case "quiesce" =>
        send(client, "ok")
        server.quiesce()
        (new Thread { override def run() { Thread.sleep(100); AdminService.stop() } }).start()
      case "stats" =>
        send(client, Map("jvm" -> Stats.getJvmStats, "counters" -> Stats.getCounterStats,
                         "timings" -> Stats.getTimingStats(false), "gauges" -> Stats.getGaugeStats(false)))
      case "server_info" =>
        send(client, Map("name" -> runtime.jarName, "version" -> runtime.jarVersion,
                         "build" -> runtime.jarBuild, "build_revision" -> runtime.jarBuildRevision))
      case x =>
        sendError(client, "Unknown command: " + x)
    }
  }

  private def send(client: Socket, data: Any) {
    send(client, "200", "OK", data)
  }

  private def sendError(client: Socket, message: String) {
    send(client, "400", "ERROR", Map("error" -> message))
  }

  private def send(client: Socket, code: String, codeDescription: String, data: Any) {
    val out = new OutputStreamWriter(client.getOutputStream())
    out.write("HTTP/1.0 %s %s\n".format(code, codeDescription))
    out.write("Server: %s/%s %s %s\n".format(runtime.jarName, runtime.jarVersion, runtime.jarBuild, runtime.jarBuildRevision))
    out.write("Content-Type: text/plain\n")
    out.write("\n")
    out.write(Json.build(data).toString + "\n")
    out.flush()
  }

  def start() {
    startupLatch = new CountDownLatch(1)
    thread.start()
    startupLatch.await()
  }

  def stop() {
    thread.interrupt()
    thread.join()
  }
}
