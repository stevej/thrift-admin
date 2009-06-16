package com.twitter.service.admin

trait ServerInterface {
  def shutdown(): Unit

  def quiesce(): Unit
}
