  
# grpc-spring-chat  
Spring Boot grpc chat demo  
  
### 1. install spring-shell-lib  
  
clone https://github.com/dreamworkerln/spring-shell-lib  
cd spring-shell-lib  
mvn -DskipTests clean install  
  
  
### 2. package grpc-spring-chat  
cd grpc-spring-chat  
mvn -DskipTests clean package  
  
For Intellij Idea after mvn compile mark folder (in Project)    
"grpc-spring-chat/shared-resources/target/generated-sources" as "Generated Sources Root"  
  
### 3. run server and client  
in client console run  
shell:$ connect  
  
login:any  
password:1  

#### server:  
NettyServerBuilder  
&nbsp;&nbsp;&nbsp;&nbsp;.forPort(port)  
&nbsp;&nbsp;&nbsp;&nbsp;.permitKeepAliveWithoutCalls(true)  
&nbsp;&nbsp;&nbsp;&nbsp;.maxConnectionIdle(MAX_CONNECTION_IDLE, TimeUnit.SECONDS)  
&nbsp;&nbsp;&nbsp;&nbsp;.keepAliveTime(KEEP_ALIVE_TIME, TimeUnit.SECONDS)  
&nbsp;&nbsp;&nbsp;&nbsp;.keepAliveTimeout(KEEP_ALIVE_TIMEOUT, TimeUnit.SECONDS)  
&nbsp;&nbsp;&nbsp;&nbsp;.permitKeepAliveTime(PERMIT_KEEP_ALIVE_TIME, TimeUnit.SECONDS)  
&nbsp;&nbsp;&nbsp;&nbsp;.addService(ServerInterceptors.intercept(new ChatService(), new HeaderInterceptor()))  
  
authentication via login/password / token in message header  
  
  
  
#### client:  
&nbsp;&nbsp;&nbsp;&nbsp;.keepAliveTime(KEEP_ALIVE_TIME, TimeUnit.SECONDS)  
&nbsp;&nbsp;&nbsp;&nbsp;.keepAliveTimeout(KEEP_ALIVE_TIMEOUT, TimeUnit.SECONDS)  
&nbsp;&nbsp;&nbsp;&nbsp;.keepAliveWithoutCalls(true)  
  
blockingStub.withDeadlineAfter(DEADLINE_DURATION, TimeUnit.MILLISECONDS)  
***  
???
dunno  
asyncStub.withDeadlineAfter ?
> But in short, you can't forcefully close a connection based on an RPC on server  
https://github.com/grpc/grpc-java/issues/779
---
&nbsp;  

So not authenticated client will receive GOAWAY after several(4) ping.   
Authenticated client may stay connected to server as much as it wants.  
Both server and client detects disconnect, 
client will periodicaly try to reconnect using built-in algorithm  
(client being behind NAT, crashed client/server)  


### materials:
grpc-java  
https://www.youtube.com/watch?v=xpmFhTMqWhc
https://www.youtube.com/watch?v=BOW7jd136Ok
https://github.com/saturnism/grpc-by-example-java  

spring-shell  
https://medium.com/agency04/developing-cli-application-with-spring-shell-part-2-4be6ce252678  
https://github.com/dmadunic/clidemo  
  


&nbsp;        
![GUI](https://i.ibb.co/KFtWgGk/2020-02-07-02-29-15.png)  
