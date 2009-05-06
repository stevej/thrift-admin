#!/usr/bin/env ruby

# may need to change this to wherever the admin ruby files are kept.
$:.push("./target/gen-rb")

require 'rubygems'
require 'getoptlong'
require 'thrift'
require 'Admin'


$port = 9991

def usage
  puts """
admin.rb commands:

info        server info (name, version, build)
stats       raw dump of server stats, for debugging
reload      reload config file, if supported
shutdown    shutdown the server cleanly

"""
end

opts = GetoptLong.new(
  [ '-h', '--help', GetoptLong::NO_ARGUMENT ],
  [ '-p', GetoptLong::REQUIRED_ARGUMENT ]
  )

opts.each do |opt, arg|
  case opt
  when '-h'
    usage
    exit 0
  when '-P'
    $port = arg.to_i
  end
end

if ARGV.empty?
  usage
  exit 0
end

transport = Thrift::BufferedTransport.new(Thrift::Socket.new("localhost", $port))
transport.open()
client = Admin::Client.new(Thrift::BinaryProtocol.new(transport))

ARGV.each do |command|
  case command
  when 'info'
    info = client.serverInfo()
    puts
    puts "Server:   #{info.name}"
    puts "Version:  #{info.version}"
    puts "Build:    #{info.build}"
    puts "Revision: #{info.build_revision}"
  when 'reload'
    puts
    puts "Reloading..."
    client.reload()
  when 'shutdown'
    puts
    puts "Shutting down..."
    client.shutdown()
  when 'stats'
    stats = client.stats(false)
    [ [stats.jvm, "JVM"], [stats.counters, "COUNTERS"] ].each do |table, table_name|
      puts "#{table_name}:"
      table.each do |name, value|
        puts "    #{name}=#{value.inspect}"
      end
    end
    puts "TIMINGS:"
    stats.timings.each do |name, timing|
      puts "    #{name}={ count=#{timing.count} average=#{timing.average} min=#{timing.minimum} max=#{timing.maximum}}"
    end
  end
end
puts
