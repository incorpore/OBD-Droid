@echo off
mode %1 38400,n,8,1
java -cp .;AgnisServer.jar com.obddroid.ecu.ObdTestFrame %1 %2 %3 %4 %5

