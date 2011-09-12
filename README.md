# ClojureSphere

Browsable dependency graph of Clojure projects. See it live here: http://clojuresphere.herokuapp.com/

## Caveats

* I intentionally built this with the false-but-useful assumption that projects are uniquely identified by their artifact ID, to make the interface simple and understandable. You can still find group IDs for projects with overlapping artifact IDs.
* Usage counts include current *and* historical dependencies.
* Dev-dependencies are included (might provide a way to filter them out)
* There may be project data missing here and there due to shortcuts taken when parsing project.clj and pom.xml files.
* Updating the list of projects is a manual process (see `preprocess.clj`). I will probably turn it into a cron job when I get a chance.
* Only projects from GitHub and Clojars are included. Other sources may be added at some point.

## TODO

- separate/distinguish dev dependencies
  - different color or icon?
  - toggle to filter out entirely?
- ~~attach a "last updated" date to projects, when available~~
- ~~move stats, last modified calc to model~~
- css
  - use clearfix
  - bigger click target for page heading
- handle ajax 404
- ~~remove _search url, pass query to /~~
- breadcrumb nav?
- show overall stats in sidebar
  - total projects
  - from github, clojars
  - # of projects over time
- tabs on home for top / recently-updated / random
- dependents sort: most-used, last updated, alphabetical
- sort dependencies by most-used
- show a project's transitive dependencies
  - as a tree/graph? arborjs?
- in project-version-detail, show only most-current version for a group/artifact combo?
- show best project homepage url
- see about getting timely sql dumps from clojars
  - created/updated timestamps
- automate data fetching & preprocessing
  - incremental github updates based on push date
- proper project.clj and pom.xml parsing
  - including version ranges (issue #1)
  - http://maven.apache.org/pom.html
  - special case for pom.contrib?
- look for project.clj in sub-dirs (e.g., ring)
- ~~clean up layout.clj code~~
- put controller fns between routes & layout fns?
- show github/clojars links for specific project versions
- toggle to exclude historical versions from counts?

## License

Copyright (C) 2011 Justin Kramer

Distributed under the Eclipse Public License, the same as Clojure.
