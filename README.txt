
Thrift-admin adds a thrift RPC service to your scala process, as defined in:

    /src/thrift/admin.thrift

Dependencies: configgy (>= 1.2), scala-stats (>= 1.0)

The admin interface can be used to ping your server, fetch the collected
stats from scala-stats, reload the server's config file, and shutdown.

The RPC port is defined in configgy via `admin_port`. Default is 9991.

A sample ruby script is included (dist/admin/scripts/admin.rb) to demonstrate
how to talk to the admin RPC.
