#!/usr/bin/env ruby

require 'socket'
require 'getoptlong'


VERSION_1 = 0x8001

module MessageTypes
  CALL = 1
  REPLY = 2
  EXCEPTION = 3
end

module Types
  STOP = 0
  VOID = 1
  BOOL = 2
  BYTE = 3
  DOUBLE = 4
  I16 = 6
  I32 = 8
  I64 = 10
  STRING = 11
  STRUCT = 12
  MAP = 13
  SET = 14
  LIST = 15
end

FORMATS = {
  Types::BYTE => "c",
  Types::DOUBLE => "G",
  Types::I16 => "n",
  Types::I32 => "N",
}

SIZES = {
  Types::BYTE => 1,
  Types::DOUBLE => 8,
  Types::I16 => 2,
  Types::I32 => 4,
}


class TCPSocket
  def write_all(data)
    n = 0
    while n < data.size
      n += write(data.slice(n, data.size))
    end
  end

  def read_all(len)
    data = ""
    while data.size < len
      data += read(len - data.size)
    end
    data
  end
end

def pack_value(type, value)
  case type
  when Types::BOOL
    [ value ? 1 : 0 ].pack("c")
  when Types::STRING
    [ value.size, value ].pack("Na*")
  when Types::I64
    [ value >> 32, value & 0xffffffff ].pack("NN")
  when Types::STRUCT
    pack_struct(value)
  else
    [ value ].pack(FORMATS[type])
  end
end

def read_value(s, type)
  case type
  when Types::BOOL
    s.read_all(1).unpack("c").first != 0
  when Types::STRING
    len = s.read_all(4).unpack("N").first
    s.read_all(len)
  when Types::I64
    hi, lo = s.read_all(8).unpack("NN")
    (hi << 32) | lo
  when Types::STRUCT
    read_struct(s)
  when Types::MAP
    read_map(s)
  else
    s.read_all(SIZES[type]).unpack(FORMATS[type]).first
  end
end

def pack_field(name, type, fid, value)
  [ type, fid, pack_value(type, value) ].pack("cna*")
end

def read_field(s)
  type = s.read_all(1).unpack("c").first
  return nil if type == Types::STOP
  fid = s.read_all(2).unpack("n").first
  read_value(s, type)
end

def pack_struct(fields)
  fields.map { |field| pack_field(*field) }.join + [ Types::STOP ].pack("c")
end

def read_struct(s)
  fields = []
  field = read_field(s)
  while !field.nil?
    fields << field
    field = read_field(s)
  end
  fields
end

def read_map(s)
  ktype, vtype, len = s.read_all(6).unpack("ccN")
  rv = {}
  len.times do
    key = read_value(s, ktype)
    value = read_value(s, vtype)
    rv[key] = value
  end
  rv
end

def read_list(s)
  etype, len = s.read_all(5).unpack("cN")
  rv = []
  len.times do
    rv << read_value(s, etype)
  end
  rv
end

def pack_call(method_name, *args)
  [ VERSION_1, MessageTypes::CALL, method_name.size, method_name, 0, pack_struct(args) ].pack("nnNa*Na*")
end

def read_response(s)
  version, message_type, method_name_len = s.read_all(8).unpack("nnN")
  method_name = s.read_all(method_name_len)
  seq_id = s.read_all(4).unpack("N").first
  [ method_name, seq_id, read_struct(s) ]
end


## ----------------------------------------


def server_info(s)
  s.write_all(pack_call("serverInfo"))
  name, seq_id, response = read_response(s)
  response = response.first
  puts "Server:   #{response[0]}"
  puts "Version:  #{response[1]}"
  puts "Build:    #{response[2]}"
  puts "Revision: #{response[3]}"
end

def stats(s)
  s.write_all(pack_call("stats", [ "reset", Types::BOOL, -1, false ]))
  name, seq_id, response = read_response(s)
  response = response.first

  [ [ response[0], "JVM" ], [ response[1], "COUNTERS" ] ].each do |table, table_name|
    puts "#{table_name}:"
    table.each do |name, value|
      puts "    #{name}=#{value.inspect}"
    end
  end
  puts "TIMINGS:"
  response[2].each do |name, timing|
    puts "    #{name}={ count=#{timing[0]} average=#{timing[3]} min=#{timing[1]} max=#{timing[2]} }"
  end
end


s = TCPSocket.new("localhost", 9991)
puts
server_info(s)
puts
stats(s)
puts
s.close
