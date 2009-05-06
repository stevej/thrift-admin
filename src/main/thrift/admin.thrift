// Copyright 2009 Twitter, Inc.

/**
 * Thrift API for administering services.
 */

namespace java com.twitter.service.admin

struct Timing {
  1: i32 count
  2: i32 minimum
  3: i32 maximum
  4: i32 average
}

typedef map<string, i64> StatsGroup
typedef map<string, double> StatsDGroup
typedef map<string, Timing> TimingStats

struct StatsResult {
  1: StatsGroup jvm
  2: StatsGroup counters
  3: TimingStats timings
  4: StatsDGroup gauges  
}

struct ServerInfo {
  1: string name
  2: string version
  3: string build
  4: string build_revision
}


service Admin {
  /**
   * Returns the binary data passed in, as a verification that the server is alive and
   * functioning.
   */
  binary ping(binary data)
  
  void reload_config()
  
  /**
   * Cleanly shuts down the server. This call may return before the server finishes shutting
   * down, but promises that the shutdown process has begun before returning.
   */
  void shutdown()
  
  /**
   * Stops the server immediately, via System.exit, without regard to a clean shutdown.
   */
  void die()
  
  /**
   * Returns a collection of server statistics, categorized by type. Timing stats are read
   * destructively if "reset" is true.
   */
  StatsResult stats(bool reset)

  ServerInfo serverInfo()
}
