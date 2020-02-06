# grpc-spring-chat  
Spring Boot grpc chat demo

####1. install spring-shell-lib   

clone git@github.com:dreamworkerln/spring-shell-lib.git  
cd spring-shell-lib  
mvn -DskipTests clean install


####2. package grpc-spring-chat  
cd grpc-spring-chat  
mvn -DskipTests clean package  

For Intellij Idea after compile mark folder (in Project)  
"grpc-spring-chat/shared-resources/target/generated-sources" as "Generated Sources Root"  

####3. run server and client  
   
in client console run     
shell:$ connect    

  
login:any  
password:1  
