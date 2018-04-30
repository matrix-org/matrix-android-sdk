#!/usr/bin/env bash

echo "Delete /docs..."
rm -rf ./docs

echo "Generate Javadoc..."
./gradlew releaseDocs

echo "Compress..."
cd ./docs
tar -zcvf matrix-sdk-javadoc.tar.gz ./javadoc/
cd ..

echo "Success!"
