  
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
  
### used:  
  
#### server:  
NettyServerBuilder  
&nbsp;&nbsp;&nbsp;&nbsp;.permitKeepAliveWithoutCalls(true)  
&nbsp;&nbsp;&nbsp;&nbsp;.permitKeepAliveTime(5, TimeUnit.SECONDS)  
  
authentication via login/password / token in message header  
  
  
  
#### client:  
channelBuilder  
&nbsp;&nbsp;&nbsp;&nbsp;.keepAliveTime(10, TimeUnit.SECONDS)  
&nbsp;&nbsp;&nbsp;&nbsp;.keepAliveTimeout(20, TimeUnit.SECONDS)  
&nbsp;&nbsp;&nbsp;&nbsp;.keepAliveWithoutCalls(true)  
  
blockingStub.withDeadlineAfter(DEADLINE_DURATION, TimeUnit.MILLISECONDS)  
  
dunno  
asyncStub.withDeadlineAfter ?  
  
  
### later  
Later going to  
&nbsp;&nbsp;&nbsp;&nbsp;.keepAliveWithoutCalls(false)  
and implement manual keepalive rpc (only for authenticated clients) because  
if .keepAliveWithoutCalls(true) then unauthenticated clients can produce keepalive messages.  
And you cannot kick user from server.  
  
> But in short, you can't forcefully close a connection based on an RPC on server  
https://github.com/grpc/grpc-java/issues/779  
  
  
  
  
  
  
![GUI](https://i.ibb.co/KFtWgGk/2020-02-07-02-29-15.png)  
