ADR: Wiki
=============

Goose's documentation will be stored in [cljdoc](https://cljdoc.org/) instead of [Github Wiki](https://docs.github.com/en/communities/documenting-your-project-with-wikis/about-wikis).

Rationale
---------

- Cljdocs are Clojure community's defacto place for hosting documentation
- It is possible to [Preview & Test](https://github.com/cljdoc/cljdoc/blob/master/doc/userguide/for-library-authors.adoc#testing--verifying) cljdocs before a release
- For code-changes in-between Clojar releases, cljdocs has a [a re-build feature](https://github.com/cljdoc/cljdoc/blob/master/doc/userguide/for-library-authors.adoc#doc-build-triggers).

Avoided Designs
---------

- Although Github wiki has [version-control](https://docs.github.com/en/communities/documenting-your-project-with-wikis/adding-or-editing-wiki-pages#adding-or-editing-wiki-pages-locally), updating is a pain. 
