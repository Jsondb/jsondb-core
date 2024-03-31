#!/bin/bash

#set version in pom.xml
version=$(mvn exec:exec -Dexec.executable=echo -Dexec.args='${project.version}' --quiet | sed 's/\.[^.]*$//')
date=$(date '+%Y%m%d')
buildnr=$1
newversion="$version.$date$buildnr"
echo "$thirdstring"
mvn versions:set -DnewVersion="$newversion"

#deploy to a maven repo.
mvn deploy

#set git tag
remote=$(git remote)
git tag "$newversion"
git push "$remote" "$newversion"

mvn versions:revert
