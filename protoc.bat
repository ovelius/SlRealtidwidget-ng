rem Requires protoc and protoc-gen-javalite
rem https://search.maven.org/search?q=g:com.google.protobuf
protoc.exe messages.proto --javalite_out=app/src/main/java