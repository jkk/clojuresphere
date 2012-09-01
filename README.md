# ClojureSphere

Browsable dependency graph of Clojure projects. See it live here: http://www.clojuresphere.com/

## Caveats

* I intentionally built this with the false-but-useful assumption that projects are uniquely identified by their artifact ID, to make the interface simple and understandable. You can still find group IDs for projects with overlapping artifact IDs.
* Usage counts include current *and* historical dependencies.
* Dev-dependencies are included (might provide a way to filter them out)
* There may be project data missing here and there due to shortcuts taken when parsing project.clj and pom.xml files.
* Updating the list of projects is a manual process (see `preprocess.clj`). I will probably turn it into a cron job when I get a chance.
* Only projects from GitHub and Clojars are included. Other sources may be added at some point.

## TODO

- change "watchers" to "stars"?
- move to a real database? Neo4J, other?
- clojure version for best/latest project version
- add rel=prev/next
- experiment with long-running threads on heroku
- store latest version at top-level
- separate/distinguish dev dependencies
  - different color or icon?
  - toggle to filter out entirely?
- handle ajax 404
- breadcrumb nav?
- "activity" field, to indicate how active a github project is
  - N commits in last month?
- pull in names of github watchers
  - count of all distinct watchers/owners
- dependents sort: most-used, last updated, alphabetical
- sort dependencies by most-used
- show a project's transitive dependencies
  - as a tree/graph? arborjs?
- in project-version-detail, show only most-current version for a group/artifact combo?
- see about getting timely sql dumps from clojars
  - created/updated timestamps
- automate data fetching & preprocessing
  - incremental github updates based on push date
- proper project.clj and pom.xml parsing
  - including version ranges (issue #1)
  - http://maven.apache.org/pom.html
  - special case for pom.contrib?
- look for project.clj in sub-dirs (e.g., ring)
- put controller fns between routes & layout fns?
- show github/clojars links for specific project versions
- toggle to exclude historical versions from counts?
- marker/filter for java projects?
- user-defined tags
- styling
  - ajax loading indicator
  - use clearfix
  - bigger click target for page heading

## License

Copyright (C) 2011-2012 Justin Kramer

Distributed under the Eclipse Public License, the same as Clojure.
