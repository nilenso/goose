Performance
============

<table>
<tr>
  <th>Version</th>
  <th>Specs</th>
  <th>Broker</th>
  <th>Latency</th>
  <th>Threads</th>
  <th>Time to process<br>100k Jobs</th>
  <th>Throughput</th>
</tr>
<tr>
  <td rowspan="2"><b>Goose:</b> 0.1<br><b>Clojure:</b> v1.11.0</td>
  <td rowspan="2"><b>OS:</b> Ubuntu 20.04<br><b>CPU:</b> AMD 4-core<br><b>RAM:</b> 8 GB</td>
  <td rowspan="2">Redis</td>
  <td rowspan="2" align="center">2 ms</td>
  <td align="center">10</td>
  <td align="center">27 sec</td>
  <td align="center">3700 Jobs/sec</td>
</tr>
<tr align="center">
  <td>25</td>
  <td>12 sec</td>
  <td>8300 Jobs/sec</td>
</tr>
</table>

Notes
---------

- Brokers have a latency of 1ms
- Performance testing code can be found here:
  - [Redis](https://github.com/nilenso/goose/tree/main/perf/redis)
- VM setup code can be found at [setup.sh](https://github.com/nilenso/goose/blob/main/perf/setup.sh)
