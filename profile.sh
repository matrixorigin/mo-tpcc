#!/usr/bin/env bash
WORKSPACE=$(cd `dirname $0`; pwd)
if [ $# -ne 1 ] ; then
    echo "usage: $(basename $0) PROPS_FILE" >&2
    exit 2
fi

if [ -f ${WORKSPACE}/report/.run ]; then
  rm -rf ${WORKSPACE}/report/.run
fi

function profile() {
    local props=$1
    if [ -f ${props} ]; then
      local addr=`cat ${props} | grep profAddr | awk -F '=' '{print $2}'`
      if [ "${addr}"x == x ]; then
        echo "`date +'%Y-%m-%d %H:%M:%S'` The profile server addr is not configured, please check."
        exit 0;
      fi 
      
      local port=`cat ${props} | grep profPort | awk -F '=' '{print $2}'`
      if [ "${port}"x == x ]; then
        echo "`date +'%Y-%m-%d %H:%M:%S'` The profile server port is not configured, please check."
        exit
      fi
            
      local think=`cat ${props} | grep profThink | awk -F '=' '{print $2}'`
      if [ "${port}"x == x ]; then
        think=60
      fi
      
      echo "`date +'%Y-%m-%d %H:%M:%S'` The profile server addr is ${addr}"
      echo "`date +'%Y-%m-%d %H:%M:%S'` The profile server port is ${port}"
    else
      echo "`date +'%Y-%m-%d %H:%M:%S'` File ${props} does not exists.please check..... "
      exit 0;
    fi
    
    echo "`date +'%Y-%m-%d %H:%M:%S'` Start to sleep for ${think} second, please wait...."
    sleep ${think}
    
    if [ -f ${WORKSPACE}/report/.run ]; then
      local runid=`cat ${WORKSPACE}/report/.run`
      echo "runid=${runid}"
      OLD_IFS="$IFS"
      IFS=","
      addrs=(${addr})
      IFS="$OLD_IFS"
      
      for ad in ${addrs[@]}
      do
        echo "`date +'%Y-%m-%d %H:%M:%S'` Start to get profile from server ${ad}" 
        mkdir -p ${WORKSPACE}/${runid}/prof/${ad}
        curl http://${ad}:${port}/debug/pprof/profile?seconds=30 > ${WORKSPACE}/${runid}/prof/${ad}/cpu
        curl http://${ad}:${port}/debug/pprof/heap > ${WORKSPACE}/${runid}/prof/${ad}/heap
        curl http://${ad}:${port}/debug/pprof/goroutine?debug=2 -o ${WORKSPACE}/${runid}/prof/${ad}/goroutine.log
       #curl http://${ad}:${port}/debug/pprof/trace?seconds=10 -o ${WORKSPACE}/${runid}/prof/${ad}/trace.out
        echo "`date +'%Y-%m-%d %H:%M:%S'` Finish to get profile from server ${ad}"
      done 
    else
      exit 0;
    fi 
}

profile $1