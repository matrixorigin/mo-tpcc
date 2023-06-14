#!/usr/bin/env bash
if [ $# -ne 1 ] ; then
    echo "usage: $(basename $0) PROPS_FILE" >&2
    exit 2
fi

WORKSPACE=$(cd `dirname $0`; pwd)
PROPS_FILE=$1
source funcs.sh $1

setCP || exit 1

myOPTS="-Dprop=$1"
echo "$myCP"

function checkError() {
  local wareNum=`grep 'warehouses' ${PROPS_FILE} | awk -F '=' '{print $2}'`
  local termNum=`grep 'terminals' ${PROPS_FILE} | awk -F '=' '{print $2}'`
  
  if [ ! -f ${WORKSPACE}/benchmarksql-error.log ];then
    echo "There is no benchmarksql-error.log."
    return 0;
  else
    result=`grep "UNEXPECTED" ${WORKSPACE}/benchmarksql-error.log`
    if [ "${result}"x != x ];then
      echo "There are some unexpected error in benchmarksql-error.log."
      echo "${result}"
      mv ${WORKSPACE}/benchmarksql-error.log ${WORKSPACE}/benchmarksql-error-${wareNum}-${termNum}.log
      return 1;
    fi
  fi 
}

java -cp "$myCP" $myOPTS io.mo.ConsistencyCheck 

if [ $? != 0 ]; then
  exit 1;
fi

checkError
