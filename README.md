# ClojureSphere

Browsable dependency graph of Clojure projects. See it live here: http://clojuresphere.herokuapp.com/

## Caveats

* I intentionally built this with the false-but-useful assumption that projects are uniquely identified by their artifact ID, to make the interface simple and understandable. You can still find group IDs for projects with overlapping artifact IDs.
* There may be project data missing here and there due to shortcuts taken when parsing project.clj and pom.xml files.
* Updating the list of projects is a manual process. I will probably turn it into a cron job when I get a chance.
* Only projects from GitHub and Clojars are included. Other sources may be added at some point.

## License

Copyright (C) 2011 Justin Kramer

Distributed under the Eclipse Public License, the same as Clojure.
