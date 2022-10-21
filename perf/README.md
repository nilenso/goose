Performance
============

<table>
<tr>
  <th>Version</th>
  <th>System Specs</th>
  <th>Broker</th>
  <th>Threads</th>
  <th>Persistence</th>
  <th>Latency</th>
  <th>Time to process<br>100k Jobs</th>
  <th>Throughput</th>
</tr>
<tr align="center">
  <td rowspan="6" align="left"><b>Goose:</b> 0.3<br><b>Clojure:</b> v1.11.0</td>
  <td rowspan="6" align="left"><b>OS: </b>Ubuntu 20.04<br><b>CPU: </b>Intel 8280<br> <b>Cores: </b>4<br><b>RAM: </b>8 GB</td>
  <td rowspan="4"> Redis</td>
  <td rowspan="6">▲<br>|<br>25<br>|<br>▼</td>
  <td>Memory</td>
  <td>2 ms</td>
  <td>12 sec</td>
  <td>8300 Jobs/sec</td>
</tr>
<tr align="center">
  <td>Batch</td>
  <td><i>wip</i></td>
  <td><i>wip</i></td>
  <td><i>wip</i></td>
</tr>
<tr align="center">
  <td>AOF</td>
  <td><i>wip</i></td>
  <td><i>wip</i></td>
  <td><i>wip</i></td>
</tr>
<tr align="center">
  <td>Cluster<br>(3-Nodes)</td>
  <td><i>wip</i></td>
  <td><i>wip</i></td>
  <td><i>wip</i></td>
</tr>
<tr align="center">
  <td rowspan="2">RabbitMQ</td>
  <td>Disk</td>
  <td>2 ms</td>
  <td>15 sec</td>
  <td>6700 Jobs/sec</td>
</tr>
<tr align="center">
  <td>Cluster<br>(3-Nodes)</td>
  <td><i>wip</i></td>
  <td><i>wip</i></td>
  <td><i>wip</i></td>
</tr>
</table>

Notes
---------

- Brokers have a latency of 1ms
- Performance testing code can be found here:
  - [Redis](https://github.com/nilenso/goose/tree/main/perf/goose/redis)
  - [RabbitMQ](https://github.com/nilenso/goose/tree/main/perf/goose/rmq)
- Results can be found in [screenshots](https://github.com/nilenso/goose/tree/main/perf/screenshots)
- VM setup code can be found at [setup.sh](https://github.com/nilenso/goose/blob/main/perf/setup.sh)
