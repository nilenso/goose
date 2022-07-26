ADR: Redis
=============

After spiking various options available, we've chosen [Carmine](https://github.com/ptaoussanis/carmine) for Redis client library.

Rationale
---------

#### Comparison

<table>
<tr>
  <th>Features >></th>
  <th>Close Connection</th>
  <th>Multiple Connections</th>
  <th>Redis 6.2.0+</th>
</tr>
<tr align="center">
  <td><a href="https://github.com/ptaoussanis/carmine">Carmine</a></td>
  <td>NO</td>
  <td>YES</td>
  <td>NO</td>
</tr>
<tr align="center">
  <td><a href="https://github.com/lerouxrgd/celtuce">Celtuce</a></td>
  <td>YES</td>
  <td>COMPLICATED</td>
  <td>NO</td>
</tr>
<tr align="center">
  <td><a href="https://github.com/tolitius/obiwan">Obiwan</a></td>
  <td colspan="3">Redis List commands aren't supported 😕</td>
</tr>
</table>

#### Carmine issues
- [Closing an active connection immediately](https://github.com/ptaoussanis/carmine/issues/266)
- [Support for Redis 6.2.0](https://github.com/ptaoussanis/carmine/issues/268)

Avoided Designs
---------

- [Celtuce](https://github.com/lerouxrgd/celtuce) & [Obiwan](https://github.com/tolitius/obiwan) do help with closing redis connections immediately
  - Obiwan isn't a full-featured Redis client library
  - Maintaining multiple connections to Redis is quite complicated with Celtuce, the clojure wrapper of Lettuce. During spike, only 1 thread had access to a sync connection
  - With Celtuce, we ran into some random bugs like thread not closing, unexpected serializations, etc. during spiking
