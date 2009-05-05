package com.twitter.service.admin

import org.specs.runner.SpecsFileRunner

object TestRunner extends SpecsFileRunner("src/test/scala/**/*.scala", ".*",
  System.getProperty("system", ".*"), System.getProperty("example", ".*"))
