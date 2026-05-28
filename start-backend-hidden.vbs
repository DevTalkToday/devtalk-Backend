Set shell = CreateObject("WScript.Shell")
shell.CurrentDirectory = "C:\Users\user\Documents\devtalk\devtalk-backend\demo"
shell.Run "cmd.exe /d /c ""C:\Users\user\Documents\devtalk\devtalk-backend\demo\start-backend.cmd""", 0, False
