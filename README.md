# Javardair

## About
This is a collaborative code writing system for Java that allows multiple parties to edit the same code base asynchronously, but with synchronous verification of incompatiblities. The goal is to allow parallel development that minimizes merge issues by keeping a online verification of conflicts. 

The system was built on top of the [Javardise](https://github.com/andre-santos-pt/javardise) projectional editor for editing the code and the [Jaid](https://github.com/adrts-iscte/Jaid) library to extract and merge the code transformations.

> This project as well as the dependent ones are research prototypes, far from being suitable for real development settings.

## Experiment
There are two types of entities in the system: *server* and *client*. The server is responsible for managing a shared code base, whereas the clients connect to the server for evolving it in a coordinated manner. These communicate through JSON messages over TCP/IP.

To facilitate experimentation, both components are packed in a single JAR file (see [Releases](https://github.com/andre-santos-pt/javardair/releases)).

### Server
The following command will start the server. The optional trunk path argument can be used to specify a local path to a trunk folder. If not provided, the server will consider the execution directory as the root of the code base (trunk).
```
java -jar javardair-X.Y.Z.jar <port> [<trunk-path>]
```

Once the server is up, clients may connect to it without any form of authentication.

### Clients
The client is the [Javardise](https://github.com/andre-santos-pt/javardise) editor with a plugin for Javardair. The server address and port are expected to be given on the **.javardair** file, located in the root directory where the editor is going to be launched. The file should have the following format:

```
SERVER=localhost
PORT=8080
CLIENT-ID=Joe-Programmer
```

If no file is present, the client assumes default values (*localhost*, port 8080, and random client id).

The editor can be launched using the following command: 
```
java -cp javardair-X.Y.Z.jar [-XstartOnFirstThread] pt.iscte.javardise.editor.MainKt
```

Due to the use of the SWT GUI library, the runtime argument **-XstartOnFirstThread** is necessary if executing on MacOS. 

On load, the client will not attempt to connect to the server. The user must click on the *Connect* button. Once connected, it will receive changes that are propagated by other clients, as well as conflict notifications.

### Known issues

- Transformation extractions do not work well with files without package declaration.
- Not all transformations are covered yet.

## Build
The build is expecting JAR files in a *libs* folder.

get them here:
https://github.com/andre-santos-pt/javardise/releases/tag/1.1

and here:
https://github.com/adrts-iscte/Jaid/releases/tag/1.0


