rem Requires protoc and protoc-gen-javalite
rem https://search.maven.org/search?q=g:com.google.protobuf
protoc.exe messages.proto --java_out=lite:app/src/main/java
protoc.exe messages.proto --java_out=../Sl-Realtime-project/PublicTransportBe/src/main/java
protoc.exe messages.proto --kotlin_out=../Sl-Realtime-project/PublicTransportBe/src/main/kotlin