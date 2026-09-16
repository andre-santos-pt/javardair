# Javardise

This is a prototype of a collaborative Java IDE, built on top of the Jaid framework. It allows multiple users to edit the same code base asynchronously, but with synchronous integration.

# Run
There are two types of components: a server and a client. The server is responsible for managing the shared code base and the clients connect to the server for editing the files.

## Server
The following command will start the server. The optional trunk path argument can be used to specify a local path to a trunk folder. If not provided, the server will consider the execution directory as the root of the code base (trunk).
```
java -jar javardair-X.Y.Z.jar <port> [<trunk-path>]
```

## Clients
A client can be started with the following command, which will launch the editor. Due to the use of the SWT GUI library, the runtime argument **-XstartOnFirstThread** is necessary if executing on Mac OS. On load, the client will not attempt to connect to the server.
```
java -cp javardair-X.Y.Z.jar [-XstartOnFirstThread] pt.iscte.javardise.editor.MainKt
```
To connect to a server, the user must click the *Connect*. The server address and port are expected to be given on the **.javardair** file, located in the root directory where the editor was launched. The file should have the following format:

```
SERVER=localhost
PORT=8080
CLIENT-ID=Joe Programmer
```


# Build
The build is expecting JAR files in a *libs* folder.

get them here:
https://github.com/andre-santos-pt/javardise/releases/tag/1.1

and here:
https://github.com/adrts-iscte/Jaid/releases/tag/1.0


# Known issues

> This prototype is a work in progress. It is not yet stable and contains bugs and limitations.

> transformation extractions do not work well with files without package declaration