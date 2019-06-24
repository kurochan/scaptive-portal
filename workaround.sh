#!/bin/bash

set -x

cd `dirname $0`

# download openflowj (openflowj package on maven seems broken?)
mkdir -p lib
curl -o lib/openflowj-3.5.535.jar http://central.maven.org/maven2/org/projectfloodlight/openflowj/3.5.535/openflowj-3.5.535.jar
