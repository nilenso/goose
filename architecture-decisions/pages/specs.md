ADR: Specs
=============

**Decision**: Use specs to validate input. Specs are better than manual validations. Inspired by [next/jdbc's spec implementation](https://github.com/seancorfield/next-jdbc/blob/5ce2c327323eb5c164e60f922f051df35ea08c83/src/next/jdbc/specs.clj)

Rationale
---------

- Single-use initialization functions have manual assertion using specs. That'll fail an application at start-time, following the principle of fail-fast
- Instrumentation for frequently-used functions is optional, but recommended in Development/Staging env

### Pros
- Job enqueue performance improves by 40% when specs are disabled
  - When tested in Development & Staging env, specs can be disabled in production to reduce validations overhead
- When doing validations manually, most of the code was checking for non-matching patterns `#(not (string? %))`. Spec definitions `string?` are more intuitive & reader-friendly
 

### Cons
- Disabled specs will violate the principle of `fail-fast`
  - Debugging becomes tougher when processing half-correct data
- Since specs are defined in a different namespace, they're magical & opaque
  - This violates Clojure's philosophy of transparency & expressiveness
  - It'd be nice to have specs as part of the function definition
- Some specs are less user-friendly
  - `s/or` is non-intuitive when compared to `s/and`
  - `s/keys` & `s/map-of` aren't macro-level friendly. For instance, restricting a map to a fixed set keys is complicated


Avoided Designs
---------

- Avoided manual validations
  - Although, many popular libraries like [Claypoole](https://github.com/clj-commons/claypoole) do have manual validations
