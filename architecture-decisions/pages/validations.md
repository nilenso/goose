ADR: Validations
=============

Intent: If user uses Goose in incorrect manner, either throw an exception or have a way for user to find out exact error

Rationale
---------

- We're [re-consider current approach to validations](https://github.com/nilenso/goose/issues/23)
- [Claypoole](https://github.com/clj-commons/claypoole) also does validations same way as Goose

Avoided Designs
---------

- Not using spec or expound for validation. 
  - They just print out statements, don't return an error or throw an exception
- Not using:
  - [Metis](https://github.com/mylesmegyesi/metis), Validateur, corroborate as they don't throw an exception. The function calling it has to write custom logic :(
- Valip https://github.com/weavejester/valip
  - No customization of predicates possible
- Reference: [data validation in clojure](https://8thlight.com/blog/myles-megyesi/2012/10/16/data-validation-in-clojure.html)
