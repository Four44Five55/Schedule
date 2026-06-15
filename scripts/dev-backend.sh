#!/bin/bash
cd "$(dirname "$0")/../backend" || exit
./gradlew bootRun
