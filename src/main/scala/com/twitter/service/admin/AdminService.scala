/** Copyright 2009 Twitter, Inc. */

package com.twitter.service.admin

import com.facebook.thrift.server.{TServer, TSimpleServer}
import com.facebook.thrift.transport.TServerSocket
import com.twitter.service.Stats
import net.lag.configgy.{Config, RuntimeEnvironment, Configgy}
import net.lag.logging.Logger
import scala.collection.Map
import scala.collection.jcl


/**
 * Implementation of the thrift Admin interface that defers to a passed-in ServerInterface
 * for a few features (like shutdown). Most of the time, the AdminServer object will be more
 * useful.
 */
class AdminService(server: ServerInterface, runtime: RuntimeEnvironment) extends Admin.Iface {
  val log = Logger.get


  def ping(data: Array[Byte]): Array[Byte] = {
    log.info("admin request: ping")
    data
  }

  def reload_config(): Unit = {
    log.info("admin request: reload")
    Configgy.reload
  }

  def shutdown(): Unit = {
    log.info("admin request: shutdown")
    server.shutdown
    // stop the admin thrift server in a new thread so it doesn't confuse thrift.
    new Thread {
      override def run() = {
        Thread.sleep(100)
        AdminService.stop
      }
    }.start
  }

  def die(): Unit = {
    log.fatal("admin request: die")
    System.exit(1)
  }

  def stats(reset: Boolean) = {
    // tedious conversion to java types
    val jvmStats = new jcl.HashMap[String, java.lang.Long]
    jvmStats ++= Stats.getJvmStats.asInstanceOf[Map[String, java.lang.Long]]
    val counterStats = new jcl.HashMap[String, java.lang.Long]
    counterStats ++= Stats.getCounterStats.asInstanceOf[Map[String, java.lang.Long]]
    val timingStats = new jcl.HashMap[String, Timing]
    for ((name, timing) <- Stats.getTimingStats(reset)) {
      timingStats(name) = new Timing(timing.count, timing.minimum, timing.maximum, timing.average)
    }
    val gaugeStats = new jcl.HashMap[String, java.lang.Double]
    gaugeStats ++= Stats.getGaugeStats(reset).asInstanceOf[Map[String, java.lang.Double]]

    new StatsResult(jvmStats.underlying, counterStats.underlying, timingStats.underlying,
                    gaugeStats.underlying)
  }

  def serverInfo(): ServerInfo = {
    new ServerInfo(runtime.jarName, runtime.jarVersion, runtime.jarBuild, runtime.jarBuildRevision)
  }
}


/**
 * Thrift service that listens on a port (from the "admin_port" config) and answers Admin
 * methods, using a passed-in ServerInterface.
 */
object AdminService {
  val log = Logger.get

  var transport: TServer = _

  /**
   * Start the admin service.
   *
   * @param server the callback used to handle server-specific requests, like shutdown
   */
  def start(server: ServerInterface, runtime: RuntimeEnvironment) = {
    val port = Configgy.config.getInt("admin_port", 9991)
    transport = new TSimpleServer(new Admin.Processor(new AdminService(server, runtime)),
      new TServerSocket(port))
    log.info("Starting admin service on port %d", port)

    val thread = new Thread {
      override def run() = {
        try {
          transport.serve()
        } catch {
          case e: NullPointerException => // ignore. may happen during shutdown.
        }
      }
    }
    thread.start
  }

  /**
   * Stop the admin service.
   */
  def stop() = {
    if (transport != null) {
      transport.stop
      transport = null
    }
  }
}
