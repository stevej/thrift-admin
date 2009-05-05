/** Copyright 2008 Twitter, Inc. */
package com.twitter.service.admin

import _root_.net.lag.configgy.{Config, Configgy}
import _root_.net.lag.logging.{Level, Logger}
import com.facebook.thrift.protocol.TBinaryProtocol
import com.facebook.thrift.transport.TSocket
import com.twitter.service.Stats
import java.io.IOException
import java.net.{ConnectException, Socket}

import org.specs._
import scala.collection.jcl


class MockServerInterface extends ServerInterface {
  var askedToShutdown = false

  def shutdown() = {
    askedToShutdown = true
  }
}


object AdminServerSpec extends Specification {

  "AdminServer" should {
    doBefore {
      Configgy.configure(System.getProperty("basedir") + "/config/test.conf")
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
      client.shutdown
      server.askedToShutdown mustBe true
    }

    "provide stats" in {
      AdminService.start(new MockServerInterface, null)
      val socket = new TSocket("localhost", 9991)
      socket.open
      val client = new Admin.Client(new TBinaryProtocol(socket))

      // make some statsy things happen
      Stats.time("kangaroo_time") { Stats.incr(1, "kangaroos") }

      val stats = client.stats(false)
      jcl.Map(stats.jvm) must haveKey("uptime")
      jcl.Map(stats.jvm) must haveKey("heap_used")
      jcl.Map(stats.counters) must haveKey("kangaroos")
      jcl.Map(stats.timings) must haveKey("kangaroo_time_count")
    }


    // can't really test "die" :)
  }
}
