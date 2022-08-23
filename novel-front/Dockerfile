FROM java:8
#ADD novel-front-1.1.0.jar /root
COPY target/novel-front-3.6.2.jar /root/novel-front.jar
ENV dburl=""
ENV username=""
ENV	password=""
EXPOSE 8083
#ENTRYPOINT ["sh","-c","java -Dspring.datasource.url=${dburl} -Dspring.datasource.username=${username} -Dspring.datasource.password=${password} -jar /root/novel-front.jar"]
ENTRYPOINT ["sh","-c","java -Dsharding.jdbc.datasource.ds0.jdbc-url=${dburl} -Dsharding.jdbc.datasource.ds0.username=${username} -Dsharding.jdbc.datasource.ds0.password=${password} -jar /root/novel-front.jar"]