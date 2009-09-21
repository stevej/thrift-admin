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
   * Stop answering new requests, but keep running while existing sessions are active.
   * When the last existing session finishes, the server will shutdown cleanly.
   */
  void quiesce()

  /**
   * Returns a collection of server statistics, categorized by type. Timing stats are read
   * destructively if "reset" is true.
   */
  StatsResult stats(bool reset)

  ServerInfo serverInfo()
}
