#!/bin/bash



java -XstartOnFirstThread -cp build/dist/javardair.jar pt.iscte.javardise.editor.MainKt workspace1 &

#java -XstartOnFirstThread -cp build/dist/javardair.jar pt.iscte.javardise.editor.MainKt workspace2 &

java -cp build/dist/javardair.jar pt.iscte.javardair.ServerKt server