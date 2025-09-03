FROM @image.name@:@image.tag@
MAINTAINER @maintainer.name@
LABEL name="@target.image.name@" Vendor="@vendor.name@" Version=@target.image.tag@ release="@target.image.release@" summary="@target.image.summary@" description="@target.image.description@"
USER root
RUN mkdir /opt/app
RUN ln -s /opt/app /config
COPY --chown=@service.user@:@service.user@ --chmod=755 root/@flight.service.archive@ /config
RUN tar -xf /config/@flight.service.archive@ -C /config
RUN rm /config/@flight.service.archive@
COPY --chown=@service.user@:@service.user@ --chmod=555 root/etc /config/etc/
COPY --chown=@service.user@:@service.user@ --chmod=555 root/initscripts /opt/ibm/initScripts/
RUN echo app - @target.image.name@: @target.image.tag@ built on @timestamp.value@ >> /opt/ibm/initScripts/version.txt
RUN chmod a+rx /config /config/@flight.service.app.name@
RUN chmod a+rx-w /opt/ibm/initScripts/*.*
USER @service.user@
CMD ["/config/@flight.service.app.name@/bin/@flight.service.app.name@"]
