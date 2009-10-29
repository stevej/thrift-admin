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

import net.lag.configgy.{Config, Configgy}
import net.lag.logging.{Level, Logger}
import com.facebook.thrift.protocol.TBinaryProtocol
import com.facebook.thrift.transport.TSocket
import com.twitter.stats.Stats
import com.twitter.xrayspecs.Eventually
import java.io.IOException
import java.net.{ConnectException, Socket}

import org.specs._
import scala.collection.jcl


object AdminServiceSpec extends Specification with Eventually {

  def waitUntilThrown[T <: Throwable](ex: Class[T])(f: => Unit) = {
    waitUntil {
      try {
        f
        false
      } catch {
        case e: Throwable =>
          if (e.getClass == ex) {
            true
          } else {
            throw e
          }
      }
    }
  }

  def waitUntil(f: => Boolean): Boolean = {
    waitUntil(f, 5000)
  }

  def waitUntil(f: => Boolean, msec: Int): Boolean = {
    if (msec < 0) {
      return false
    }
    if (f) {
      return true
    }
    Thread.sleep(50)
    waitUntil(f, msec - 50)
  }

  "AdminServer" should {
    doAfter {
      AdminService.stop
    }


    "start and stop" in {
      new Socket("localhost", 9991) must throwA[ConnectException]
      AdminService.start(new MockServerInterface, null)
      new Socket("localhost", 9991) must notBeNull
      AdminService.stop
      new Socket("localhost", 9991) must throwA[ConnectException]
    }

    "answer pings" in {
      AdminService.start(new MockServerInterface, null)
      val socket = new TSocket("localhost", 9991)
      socket.open
      val client = new Admin.Client(new TBinaryProtocol(socket))
      new String(client.ping("hello!".getBytes)) mustEqual "hello!"
    }

    "answer 3 pings in a row" in {
      AdminService.start(new MockServerInterface, null)
      val socket = new TSocket("localhost", 9991)
      socket.open
      val client = new Admin.Client(new TBinaryProtocol(socket))
      new String(client.ping("hello!".getBytes)) mustEqual "hello!"
      new String(client.ping("hello?".getBytes)) mustEqual "hello?"
      new String(client.ping("goodbye...".getBytes)) mustEqual "goodbye..."
    }

    "reload config files" in {
      AdminService.start(new MockServerInterface, null)
      val socket = new TSocket("localhost", 9991)
      socket.open
      val client = new Admin.Client(new TBinaryProtocol(socket))

      Configgy.config.getString("test_value", "?") mustEqual "kangaroo"
      Configgy.config.setString("test_value", "pig")
      Configgy.config.getString("test_value", "?") mustEqual "pig"
      client.reload_config
      Configgy.config.getString("test_value", "?") mustEqual "kangaroo"
    }

    "shutdown" in {
      val server = new MockServerInterface
      AdminService.start(server, null)
      val socket = new TSocket("localhost", 9991)
      socket.open
      val client = new Admin.Client(new TBinaryProtocol(socket))

      server.askedToShutdown mustBe false
      client.shutdown()
      server.askedToShutdown mustBe true
      waitUntilThrown(classOf[ConnectException]) { new Socket("localhost", 9991) }
    }

    "quiesce" in {
      val server = new MockServerInterface
      AdminService.start(server, null)
      val socket = new TSocket("localhost", 9991)
      socket.open
      val client = new Admin.Client(new TBinaryProtocol(socket))

      server.askedToQuiesce mustBe false
      client.quiesce()
      server.askedToQuiesce mustBe true
      waitUntilThrown(classOf[ConnectException]) { new Socket("localhost", 9991) }
    }

    "provide stats" in {
      AdminService.start(new MockServerInterface, null)
      val socket = new TSocket("localhost", 9991)
      socket.open
      val client = new Admin.Client(new TBinaryProtocol(socket))

      // make some statsy things happen
      Stats.time("kangaroo_time") { Stats.incr("kangaroos", 1) }

      val stats = client.stats(false)
      jcl.Map(stats.jvm) must haveKey("uptime")
      jcl.Map(stats.jvm) must haveKey("heap_used")
      jcl.Map(stats.counters) must haveKey("kangaroos")
      jcl.Map(stats.timings) must haveKey("kangaroo_time")
      val timing = jcl.Map(stats.timings)("kangaroo_time")
      timing.count mustEqual 1
      timing.average mustEqual timing.minimum
      timing.average mustEqual timing.maximum
    }
  }
}
