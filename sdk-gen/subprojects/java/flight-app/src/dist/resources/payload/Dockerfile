FROM @image.name@:@image.tag@
MAINTAINER @maintainer.name@
LABEL name="@target.image.name@" Vendor="@vendor.name@" Version=@target.image.tag@ release="@target.image.release@" summary="@target.image.summary@" description="@target.image.description@"
USER @service.user@
COPY --chown=@service.user@:@service.user@ root/wlp/usr/servers/defaultServer /config/
#Use line below to inject public keys for client authentication
#COPY --chown=@service.user@:@service.user@ root/etc /config/etc/
COPY --chown=@service.user@:@service.user@ root/initscripts /opt/ibm/initScripts/
RUN echo app - @target.image.name@: @target.image.tag@ built on @timestamp.value@ >> /opt/ibm/initScripts/version.txt
USER root
RUN chmod a+rxw /config /config/apps /logs
RUN chmod a+rwx /opt/ibm/initScripts/*.*
USER @service.user@
RUN for i in `cat /config/server.env`; do export $i; done; \
    echo "KEYSTORE_PASSWORD=$keystore_password" > /config/bootstrap.properties; \
    echo "TRUSTSTORE_PASSWORD=changeit" >> /config/bootstrap.properties; \
    installUtility install --acceptLicense defaultServer; \
    securityUtility createSSLCertificate --password=$keystore_password --server=defaultServer
USER root
RUN chmod -R a+rwx /opt/ibm/wlp/output
USER @service.user@
