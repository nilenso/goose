Performance
============

<table>
<tr>
  <th>Version</th>
  <th>System Specs</th>
  <th>Broker</th>
  <th>Latency</th>
  <th>Threads</th>
  <th>Time to process<br>100k Jobs</th>
  <th>Throughput</th>
</tr>
<tr align="center">
  <td align="left"><b>Goose:</b> 0.2<br><b>Clojure:</b> v1.11.0</td>
  <td align="left"><b>OS: </b>Ubuntu 20.04<br><b>CPU: </b>Intel 8280<br> <b>Cores: </b>4<br><b>RAM: </b>8 GB</td>
  <td>Redis</td>
  <td>2 ms</td>
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
