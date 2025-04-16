#!/bin/bash

java -cp build/dist/javardair.jar ServerKt &

java -XstartOnFirstThread -cp build/dist/javardair.jar pt.iscte.javardise.editor.MainKt workspace1 &

java -XstartOnFirstThread -cp build/dist/javardair.jar pt.iscte.javardise.editor.MainKt workspace2 &
