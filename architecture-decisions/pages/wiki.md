ADR: Wiki
=============

Goose's documentation will be stored in [Github Wiki](https://docs.github.com/en/communities/documenting-your-project-with-wikis/about-wikis) instead of [cljdoc](https://cljdoc.org/).

Rationale
---------

- Github wiki has [version-control](https://docs.github.com/en/communities/documenting-your-project-with-wikis/adding-or-editing-wiki-pages#adding-or-editing-wiki-pages-locally).
- Documentation can evolve independently of releases.
  


Avoided Designs
---------

- [Preview & Testing](https://github.com/cljdoc/cljdoc/blob/master/doc/userguide/for-library-authors.adoc#testing--verifying) cljdocs before a release is only possible on linux desktops.
- Cljdocs has a [a re-build feature](https://github.com/cljdoc/cljdoc/blob/master/doc/userguide/for-library-authors.adoc#doc-build-triggers). However, it only works for the latest release on Clojars. You cannot update cljdocs in-between releases.
