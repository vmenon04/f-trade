# Jenkins for the Jenkinsfile at the repo root, set up from files instead of by hand: the plugins in plugins.txt and
# the settings, logins and the ftrade job in casc.yaml. Built and run by docker-compose.ci.yml.

# the JDK 25 image, because the build runs on Jenkins's own Java and pom.xml compiles for 25
FROM jenkins/jenkins:2.580.2-lts-jdk25

USER root
# the pipeline builds and runs images with the server's Docker, through its socket (see docker-compose.ci.yml), so
# only the client is needed here. buildx is what `docker build` uses for the Dockerfile's cache mount.
COPY --from=docker:27-cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker:27-cli /usr/local/libexec/docker/cli-plugins/docker-buildx /usr/local/libexec/docker/cli-plugins/docker-buildx
# the Maven3 tool the Jenkinsfile asks for
COPY --from=maven:3.9-eclipse-temurin-25 /usr/share/maven /opt/maven
USER jenkins

COPY plugins.txt /usr/share/jenkins/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/plugins.txt

COPY casc.yaml /usr/share/jenkins/casc.yaml
ENV CASC_JENKINS_CONFIG=/usr/share/jenkins/casc.yaml
