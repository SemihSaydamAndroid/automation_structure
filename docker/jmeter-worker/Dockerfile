# JMeter load generator image used by DistributedLoadTest (automation-k8s).
# Build: docker build -t <registry>/jmeter-worker:5.6.3 docker/jmeter-worker
# Plugins: docker build --build-arg PLUGINS="jpgc-casutg jpgc-tst" ...
FROM eclipse-temurin:21-jre

ARG JMETER_VERSION=5.6.3
ARG JMETER_SHA512=5978a1a35edb5a7d428e270564ff49d2b1b257a65e17a759d259a9283fc17093e522fe46f474a043864aea6910683486340706d745fcdf3db1505fd71e689083
ARG PLUGINS=""

ADD https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-${JMETER_VERSION}.tgz /tmp/jmeter.tgz
RUN echo "${JMETER_SHA512}  /tmp/jmeter.tgz" | sha512sum -c - \
    && tar -xzf /tmp/jmeter.tgz -C /opt \
    && rm /tmp/jmeter.tgz \
    && ln -s /opt/apache-jmeter-${JMETER_VERSION} /opt/jmeter \
    && rm -rf /opt/jmeter/docs /opt/jmeter/printable_docs

ENV JMETER_HOME=/opt/jmeter \
    PATH=/opt/jmeter/bin:${PATH}

ADD https://repo1.maven.org/maven2/kg/apc/jmeter-plugins-manager/1.11/jmeter-plugins-manager-1.11.jar /opt/jmeter/lib/ext/
ADD https://repo1.maven.org/maven2/kg/apc/cmdrunner/2.3/cmdrunner-2.3.jar /opt/jmeter/lib/
RUN if [ -n "${PLUGINS}" ]; then \
        java -cp /opt/jmeter/lib/ext/jmeter-plugins-manager-1.11.jar org.jmeterplugins.repository.PluginManagerCMDInstaller \
        && PluginsManagerCMD.sh install ${PLUGINS}; \
    fi \
    && chmod -R a+rX /opt/apache-jmeter-${JMETER_VERSION}

# Non-root; /results and /tmp are writable emptyDirs in the pod.
RUN useradd --uid 10001 --create-home jmeter
USER 10001
WORKDIR /home/jmeter
