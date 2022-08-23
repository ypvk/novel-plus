FROM java:8
COPY target/novel-front-3.6.2.jar /root/novel-front.jar
ENV dburl=""
ENV username=""
ENV password=""
EXPOSE 8083
ENTRYPOINT ["sh","-c","java -Dsharding.jdbc.datasource.ds0.jdbc-url=${dburl} -Dsharding.jdbc.datasource.ds0.username=${username} -Dsharding.jdbc.datasource.ds0.password=${password} -jar /root/novel-front.jar"]
