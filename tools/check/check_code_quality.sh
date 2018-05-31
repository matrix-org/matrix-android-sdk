#!/usr/bin/env bash

echo
echo "Search for forbidden patterns in code..."

./tools/check/search_forbidden_strings.pl ./tools/check/forbidden_strings_in_code.txt ./matrix-sdk/src/main/java

resultForbiddenStringInCode=$?

echo
echo "Search for forbidden patterns in resources..."

./tools/check/search_forbidden_strings.pl ./tools/check/forbidden_strings_in_resources.txt \
    ./matrix-sdk/src/main/res/layout \
    ./matrix-sdk/src/main/res/values

resultForbiddenStringInResource=$?

echo

if [ $resultForbiddenStringInCode -eq 0 ] && [ $resultForbiddenStringInResource -eq 0 ]; then
   echo "MAIN OK"
else
   echo "MAIN ERROR"
   exit 1
fi
