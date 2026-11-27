FROM mirror.gcr.io/library/eclipse-temurin:17
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH "${JAVA_HOME}/bin:${PATH}"

COPY . /opt/tech/pegasys/teku/test/acceptance/stubServer/

WORKDIR /opt/tech/pegasys/teku/test/acceptance/stubServer/

EXPOSE 8001

RUN javac /opt/tech/pegasys/teku/test/acceptance/stubServer/*.java -d bin

RUN useradd --system stub
USER stub

CMD java -cp bin tech.pegasys.teku.test.acceptance.stubServer.StubServer

