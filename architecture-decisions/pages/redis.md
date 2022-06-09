ADR: Redis
=============

Rationale
---------

- We're [reconsidering redis client library](https://github.com/nilenso/goose/issues/14)

Avoided Designs
---------

- [Celtuce](https://github.com/lerouxrgd/celtuce) & [Obiwan](https://github.com/tolitius/obiwan) do help with closing redis connections immediately
  - However, we've decided to stick with carmine because the other 2 libraries are not maintained actively, and we ran into some bugs like the thread doesn't close, unexpected serializations, etc. in REPL
