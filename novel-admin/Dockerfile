FROM java:8
COPY target/novel-admin-3.6.2.jar /root/novel-admin.jar
ENV dburl=""
ENV username=""
ENV password=""
EXPOSE 8088
ENTRYPOINT ["sh","-c","java -Dspring.datasource.url=${dburl} -Dspring.datasource.username=${username} -Dspring.datasource.password=${password} -jar /root/novel-admin.jar"]
