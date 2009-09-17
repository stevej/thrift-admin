/** Copyright 2008 Twitter, Inc. */
package com.twitter.service.admin

import net.lag.configgy.{Config, Configgy}
import net.lag.logging.{Level, Logger}
import com.facebook.thrift.protocol.TBinaryProtocol
import com.facebook.thrift.transport.TSocket
import com.twitter.stats.Stats
import java.io.IOException
import java.net.{ConnectException, Socket}

import org.specs._
import scala.collection.jcl


class MockServerInterface extends ServerInterface {
  var askedToShutdown = false
  var askedToQuiesce = false

  def shutdown() = {
    askedToShutdown = true
  }

  def quiesce() = {
    askedToQuiesce = true
  }
}


object AdminServerSpec extends Specification {

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
    doBefore {
      Configgy.configure(System.getProperty("basedir") + "/config/test.conf")
      Logger.clearHandlers
    }

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
