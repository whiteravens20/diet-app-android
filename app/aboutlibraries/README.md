# The texts of the libraries' licences

The build lists the libraries the release ships, with their licences, for the legal notices
(the AboutLibraries plugin, configured in `app/build.gradle.kts`). It asks nothing of the
network, so the text of every licence those libraries are under is kept here, in `licenses/`,
one file per licence, named by its SPDX identifier (`hash`):

- `Apache-2.0.json`: the text published at <https://www.apache.org/licenses/LICENSE-2.0.txt>.
- `BSD-3-Clause.json`: the `LICENSE` file of protobuf (v36.2), with its own copyright line. The
  one library under this licence is AndroidX's copy of protobuf
  (`androidx.datastore:datastore-preferences-external-protobuf`), and the licence asks for its
  notice to be reproduced.

A library under another licence makes the build say that the licence "does not contain the
license text and configuration is in OFFLINE MODE" (`./gradlew :app:exportLibrariesRelease`
lists every library and its licence). Add that licence's text here, from the library's own
`LICENSE` file where the licence carries a copyright line.
