#!/usr/bin/env bash
if [ $# -lt 1 ] ; then
    echo "usage: $(basename $0) PROPS_FILE" >&2
    exit 2
fi

WORKSPACE=$(cd `dirname $0`; pwd)
PROPS_FILE=$1
source funcs.sh $1

setCP || exit 1

myOPTS="-Dprop=$1"
echo "$myCP"



java -cp "$myCP" $myOPTS io.mo.ConsistencyCheck $2
