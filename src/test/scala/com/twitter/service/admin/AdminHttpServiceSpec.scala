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

import java.io.InputStream
import net.lag.configgy.RuntimeEnvironment
/*
import net.lag.logging.{Level, Logger}
import com.facebook.thrift.protocol.TBinaryProtocol
import com.facebook.thrift.transport.TSocket
import com.twitter.stats.Stats
import java.io.IOException
*/
import java.net.{ConnectException, Socket}

import org.specs._
/*import scala.collection.jcl*/

object AdminHttpServiceSpec extends Specification {
  class PimpedInputStream(stream: InputStream) {
    def readString(maxBytes: Int) = {
      val buffer = new Array[Byte](maxBytes)
      val len = stream.read(buffer)
      new String(buffer, 0, len, "UTF-8")
    }
  }
  implicit def pimpInputStream(stream: InputStream) = new PimpedInputStream(stream)

  "AdminHttpService" should {
    "start and stop" in {
      new Socket("localhost", 9990) must throwA[ConnectException]
      val service = new AdminHttpService(new MockServerInterface, null)
      service.start()
      new Socket("localhost", 9990) must notBeNull
      service.stop()
      new Socket("localhost", 9990) must throwA[ConnectException]
    }

    "answer pings" in {
      val service = new AdminHttpService(new MockServerInterface, new RuntimeEnvironment(getClass))
      service.start()
      val socket = new Socket("localhost", 9990)
      socket.getOutputStream().write("get /ping\n".getBytes)
      val buffer = new Array[Byte](1024)
      val x = socket.getInputStream().readString(1024)
      x.split("\n").last mustEqual "\"pong\""
    }
  }
}
