#!/usr/bin/env bash

#
# Copyright 2018 New Vector Ltd
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

#######################################################################################################################
# Search forbidden pattern
#######################################################################################################################

searchForbiddenStringsScript=./tmp/search_forbidden_strings.pl

if [ -f ${searchForbiddenStringsScript} ]; then
  echo "${searchForbiddenStringsScript} already there"
else
  mkdir tmp
  echo "Get the script"
  wget https://raw.githubusercontent.com/matrix-org/matrix-dev-tools/develop/bin/search_forbidden_strings.pl -O ${searchForbiddenStringsScript}
fi

if [ -x ${searchForbiddenStringsScript} ]; then
  echo "${searchForbiddenStringsScript} is already executable"
else
  echo "Make the script executable"
  chmod u+x ${searchForbiddenStringsScript}
fi

echo
echo "Search for forbidden patterns in code..."

${searchForbiddenStringsScript} ./tools/check/forbidden_strings_in_code.txt \
    ./matrix-sdk/src/main/java \
    ./matrix-sdk/src/test/java \
    ./matrix-sdk/src/androidTest/java

resultForbiddenStringInCode=$?

echo
echo "Search for forbidden patterns in resources..."

${searchForbiddenStringsScript} ./tools/check/forbidden_strings_in_resources.txt \
    ./matrix-sdk/src/main/res/layout \
    ./matrix-sdk/src/main/res/values

resultForbiddenStringInResource=$?

#######################################################################################################################
# Check files with long lines
#######################################################################################################################

checkLongFilesScript=./tmp/check_long_files.pl

if [ -f ${checkLongFilesScript} ]; then
  echo "${checkLongFilesScript} already there"
else
  mkdir tmp
  echo "Get the script"
  wget https://raw.githubusercontent.com/matrix-org/matrix-dev-tools/develop/bin/check_long_files.pl -O ${checkLongFilesScript}
fi

if [ -x ${checkLongFilesScript} ]; then
  echo "${checkLongFilesScript} is already executable"
else
  echo "Make the script executable"
  chmod u+x ${checkLongFilesScript}
fi

echo
echo "Search for long files..."

${checkLongFilesScript} 3200 \
    ./matrix-sdk/src/main/java \
    ./matrix-sdk/src/test/java \
    ./matrix-sdk/src/main/res/layout \
    ./matrix-sdk/src/main/res/values

resultLongFiles=$?

echo

if [ ${resultForbiddenStringInCode} -eq 0 ] && [ ${resultForbiddenStringInResource} -eq 0 ] && [ ${resultLongFiles} -eq 0 ]; then
   echo "MAIN OK"
else
   echo "MAIN ERROR"
   exit 1
fi
