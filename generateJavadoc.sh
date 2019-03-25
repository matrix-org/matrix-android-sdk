#!/usr/bin/env bash

echo "Generate Javadoc..."
./gradlew generateReleaseJavadoc --stacktrace

echo "Compress..."
cd ./matrix-sdk/build/docs/
tar -zcvf ../../matrix-sdk-javadoc.tar.gz ./javadoc/
cd -

echo "Success!"
