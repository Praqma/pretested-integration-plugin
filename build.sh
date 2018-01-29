#!/bin/bash

echo *** Configure git
export HOME=/var/tmp
git config --global user.email "release@praqma.net"
git config --global user.name "Praqma Release User"
git config --list
echo *** Starting build
mvn -Duser.home=/var/maven "$@"

