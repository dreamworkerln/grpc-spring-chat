
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
  
    
     
####used:

#####server:  
NettyServerBuilder  
&nbsp;&nbsp;&nbsp;&nbsp;.permitKeepAliveWithoutCalls(true)  
&nbsp;&nbsp;&nbsp;&nbsp;.permitKeepAliveTime(5, TimeUnit.SECONDS)  
  
authentication via login/password / token in message header  



#####client:  
  
channelBuilder  
&nbsp;&nbsp;&nbsp;&nbsp;.keepAliveTime(10, TimeUnit.SECONDS)  
&nbsp;&nbsp;&nbsp;&nbsp;.keepAliveTimeout(20, TimeUnit.SECONDS)  
&nbsp;&nbsp;&nbsp;&nbsp;.keepAliveWithoutCalls(true)  
            
blockingStub.withDeadlineAfter  
    
dunno  
asyncStub.withDeadlineAfter ?  
   
                
