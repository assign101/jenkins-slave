FROM nexus.devtools.syd.c1.macquarie.com:9991/bfs-docker/bfs-builder-java8:1.2


####################################
# Install JNLP slave               #
####################################

USER root

# These are writable by group so that the jenkins user can add itself to the /etc/passwd when the container is run
# That is necessary for git over ssh to work
RUN chmod 664 /etc/passwd /etc/group


COPY start-slave.sh /usr/local/bin/start-slave.sh
RUN chmod 777 /usr/local/bin/start-slave.sh

RUN mkdir /ocbin && chown 592 /ocbin && chmod 777 /ocbin

USER 592
RUN cp /usr/bin/oc* /ocbin/ && \
    chmod 777 /ocbin/*

ENV PATH=/ocbin/:$PATH

ENTRYPOINT ["/usr/local/bin/start-slave.sh"]
