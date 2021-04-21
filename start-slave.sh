#!/bin/bash

#########################################
## Setup Environment
#########################################

if [ -e /env.properties ]; then
    source /env.properties
fi

echo "Starting slave with environment:"
env

if [[ -e /usr/local/bin/configure-slave ]]; then
    source /usr/local/bin/configure-slave
fi


# Add jenkins user for the current user and group ids so that ssh (and git over ssh) will work
export USER_ID=$(id -u)
export GROUP_ID=$(id -g)
PWORD_ENTRY="jenkins:x:${USER_ID}:${GROUP_ID}:Jenkins:${HOME}:/bin/bash"
echo "${PWORD_ENTRY}" >> /etc/passwd
echo "Added ${PWORD_ENTRY} to /etc/passwd"
mkdir -p ${HOME}

###########################################################################################################################
# if HOME_SECRETS is defined, it is taken as a comma seperated list of directories
# The contents of the directories in HOME_SECRETS are copied to the home directory
# This is to allow multiple secrets to be mounted in the slave away from the home but then be copied there
# This is due to a kubernetes limitation that does not allow more than one volume to be mounted at the same place
###########################################################################################################################
if [[ ${HOME_SECRETS:+1} ]]; then
  echo "cp HOME_SECRETS ${HOME_SECRETS}"
  DIRS=(${HOME_SECRETS//,/ })
  shopt -s dotglob # turn on including hidden files (ones starting with a dot)
  for i in "${!DIRS[@]}"
  do
    echo "DIR=${DIRS[i]}"
    cp ${DIRS[i]}/* ${HOME}/
  done
else
  echo "No home secrets"
fi

# NB - this is because the existing secret for stash is of a particular form. This will eventually go but is safe to leave in as the mount point will just disappear
# Copy the mounted ssh-privatekey to the .ssh folder
# This is here because the slave needs a volume mount of sshsecret to /opt/openshift/ssh
if [ -e  /opt/openshift/ssh/ssh-privatekey ]; then
    mkdir -p ~/.ssh
    cp  /opt/openshift/ssh/ssh-privatekey ${HOME}/.ssh/id_rsa
    chmod 600 ${HOME}/.ssh/id_rsa
fi


###################################################################################
## Setup Docker logins
###################################################################################
if [[ -e /tmp/docker-registry/username && -e /tmp/docker-registry/password ]]; then
    echo "Login to the nexus docker registry"

    USERNAME=$(cat /tmp/docker-registry/username )
    PASSWORD=$(cat /tmp/docker-registry/password )
    EMAIL=a@b.com
    REGISTRY=nexus.devtools.syd.c1.macquarie.com:9991

    docker login -u $USERNAME -p $PASSWORD -e $EMAIL $REGISTRY
else
    echo "Nexus 3 secret is not mounted at /tmp/docker-registry - cannot login to Nexus 3 docker registry"
fi

if [[ -e /tmp/registrysa-local/token ]]; then
    echo "Login to the local docker registry"
    TOKEN=$(cat /tmp/registrysa-local/token)
    docker login -u registrysa -p $TOKEN -e a@b.com 172.30.150.150:5000
else
    echo "registrysa-local secret is not mounted at /tmp/registrysa-local - cannot login to local docker registry "
fi


###################################################################################
## Setup Java for running JNLP client
###################################################################################
# Download the remoting jar from the jenkins server
# NB JENKINS_URL is set by the Kubernetes cloud plugin
JAR="${HOME}/remoting.jar"
curl -ksS ${JENKINS_URL}/jnlpJars/remoting.jar -o ${JAR}

echo "Parameters passed: $@"

PARAMS=""

# if -url is not provided try env vars
if [[ "$@" != *"-url "* ]]; then
    if [ ! -z "$JENKINS_URL" ]; then
        PARAMS="$PARAMS -url $JENKINS_URL"
    elif [ ! -z "$JENKINS_SERVICE_HOST" ] && [ ! -z "$JENKINS_SERVICE_PORT" ]; then
        PARAMS="$PARAMS -url http://$JENKINS_SERVICE_HOST:$JENKINS_SERVICE_PORT"
    fi
fi

# if -tunnel is not provided try env vars
if [[ "$@" != *"-tunnel "* ]]; then
    if [ ! -z "$JENKINS_TUNNEL" ]; then
        PARAMS="$PARAMS -tunnel $JENKINS_TUNNEL"
    elif [ ! -z "$JENKINS_SLAVE_SERVICE_HOST" ] && [ ! -z "$JENKINS_SLAVE_SERVICE_PORT" ]; then
        PARAMS="$PARAMS -tunnel $JENKINS_SLAVE_SERVICE_HOST:$JENKINS_SLAVE_SERVICE_PORT"
    fi
fi

if [[ -z "${JAVA_TOOL_OPTIONS}" ]]; then
    # these options will automatically be picked up by any JVM process but can
    # be overridden on that process' command line.
    JAVA_TOOL_OPTIONS="-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -Dsun.zip.disableMemoryMapping=true"
    export JAVA_TOOL_OPTIONS
fi



CONTAINER_MEMORY_IN_BYTES=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)
CONTAINER_MEMORY_IN_MB=$((CONTAINER_MEMORY_IN_BYTES/2**20))
echo "Container Memory = ${CONTAINER_MEMORY_IN_MB}Mb"

# If memory limit less than 1TiB
if [[ "${CONTAINER_MEMORY_IN_BYTES}" -lt $((2**40)) ]]; then
    # set this JVM's -Xmx and -Xms if not set already (not propagated to any
    # child JVMs).  -Xmx can be calculated as a percentage, capped to a maximum,
    # or specified straight.  -Xms can be calculated as a percentage or
    # specified straight.  For the JNLP slave by default we specify -Xmx of 50%,
    # uncapped; -Xms unspecified (JVM default is 1/64 of -Xmx).

    if [[ -z "$CONTAINER_HEAP_PERCENT" ]]; then
        CONTAINER_HEAP_PERCENT=0.80
    fi

    CONTAINER_HEAP_MAX=$(echo "${CONTAINER_MEMORY_IN_MB} ${CONTAINER_HEAP_PERCENT}" | awk '{ printf "%d", $1 * $2 }')
    if [[ $JNLP_MAX_HEAP_UPPER_BOUND_MB && $CONTAINER_HEAP_MAX -gt $JNLP_MAX_HEAP_UPPER_BOUND_MB ]]; then
        CONTAINER_HEAP_MAX=$JNLP_MAX_HEAP_UPPER_BOUND_MB
    fi
    if [[ -z "$JAVA_MAX_HEAP_PARAM" ]]; then
        JAVA_MAX_HEAP_PARAM="-Xmx${CONTAINER_HEAP_MAX}m"
    fi

    if [[ "$CONTAINER_INITIAL_PERCENT" ]]; then
        CONTAINER_INITIAL_HEAP=$(echo "${CONTAINER_HEAP_MAX} ${CONTAINER_INITIAL_PERCENT}" | awk '{ printf "%d", $1 * $2 }')
        if [[ -z "$JAVA_INITIAL_HEAP_PARAM" ]]; then
            JAVA_INITIAL_HEAP_PARAM="-Xms${CONTAINER_INITIAL_HEAP}m"
        fi
    fi
else
    echo "More memory than sense, dont limit the java heap"
fi
echo "JAVA_MAX_HEAP_PARAM=${JAVA_MAX_HEAP_PARAM}"


if [[ -z "$JAVA_GC_OPTS" ]]; then
    # See https://developers.redhat.com/blog/2014/07/22/dude-wheres-my-paas-memory-tuning-javas-footprint-in-openshift-part-2/ .
    # The values are aggressively set with the intention of relaxing GC CPU time
    # restrictions to enable it to free as much as possible, as well as
    # encouraging the GC to free unused heap memory back to the OS.
    JAVA_GC_OPTS="-XX:+UseParallelGC -XX:MinHeapFreeRatio=5 -XX:MaxHeapFreeRatio=10 -XX:GCTimeRatio=4 -XX:AdaptiveSizePolicyWeight=90"
fi

if [[ -z "${JNLP_JAVA_OPTIONS}" ]]; then
    JNLP_JAVA_OPTIONS="$JAVA_GC_OPTS $JAVA_INITIAL_HEAP_PARAM $JAVA_MAX_HEAP_PARAM "
fi

OC_VERSION=$(oc version | grep openshift | cut -d ' ' -f 2 | cut -d 'v' -f 2 | cut -d '.' -f 1,2)
OC_FILE=/ocbin/oc${OC_VERSION}
echo "OC_VERSION=${OC_VERSION} which should be for file ${OC_FILE}"
if [[ -f $OC_FILE ]]; then
    rm -f /ocbin/oc
    ln -fs $OC_FILE /ocbin/oc
    echo "Set oc to be ${OC_FILE}"
else
    echo "No match for ${OC_FILE} - leaving on default version"
fi

if [[ -f /etc/ssh/ssh_config ]]; then
    mkdir -p ${HOME}/.ssh/
    cp /etc/ssh/ssh_config ${HOME}/.ssh/config
fi

###################################################################################
## Run JNLP client
###################################################################################
echo "Start JNLP $*"
java $JNLP_JAVA_OPTIONS $JNLP_JAVA_OVERRIDES \
                        -cp $JAR hudson.remoting.jnlp.Main \
                        -headless $PARAMS "$@"
