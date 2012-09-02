#!/bin/sh

rsync -av --exclude '*.jar' clojars.org::clojars clojars
JAVA_OPTS="-Xmx1000m" lein trampoline run -m clojuresphere.preprocess clojars/
