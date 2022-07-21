ADR: Specs
=============

Intent: If user uses Goose in incorrect manner, either throw an exception or have a way for user to find out error

Rationale
---------

- We're [re-consider current approach to validations](https://github.com/nilenso/goose/issues/23)
- [Claypoole](https://github.com/clj-commons/claypoole) also does validations same way as Goose
- Specs have an option of lowering overhead in production. Job enqueue performance improves by 20% when validations are disabled
- When doing validations manually, most code was checking for non-matching patterns. Spec definitions are more intuitive & reader-friendly

Avoided Designs
---------

- Not using spec or expound for validation. 
  - They just print out statements, don't return an error or throw an exception
- Not using:
  - [Metis](https://github.com/mylesmegyesi/metis), Validateur, corroborate as they don't throw an exception. The function calling it has to write custom logic :(
- Valip https://github.com/weavejester/valip
  - No customization of predicates possible
- Reference: [data validation in clojure](https://8thlight.com/blog/myles-megyesi/2012/10/16/data-validation-in-clojure.html)
