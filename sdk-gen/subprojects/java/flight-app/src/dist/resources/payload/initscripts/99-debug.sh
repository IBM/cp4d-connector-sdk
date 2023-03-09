#!/bin/sh

echo "ENABLE_DEBUG = $ENABLE_DEBUG."
if [ "$ENABLE_DEBUG" == "true" ]; then
  echo "Starting in Debug Mode"
  ${WLP_SERVER} debug defaultServer
  #
  # exit 1 so no other scripts are run and
  # the caller does not run "start defaultServer"
  #
  exit 1
fi
exit 0
