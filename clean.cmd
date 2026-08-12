@echo off
setlocal

set LOGFILE=clean.log

echo Checking if the log file exists...
if exist "%LOGFILE%" (
    del /q "%LOGFILE%"
    echo Existing log file deleted.
) else (
    echo Log file does not exist, continuing.
)

echo Starting cleanup...

rem Deleting unnecessary IDE files
for %%f in (*.ncb, *.user) do (
    if exist "%%f" (
        echo Deleting file: %%f
        del /q "%%f"
        echo Deleted file: %%f >> "%LOGFILE%"
    )
)

rem Deleting bin and obj directories
for /d /r %%d in (bin, obj) do (
    if exist "%%d" (
        echo Deleting directory: %%d
        rmdir /s /q "%%d"
        echo Deleted directory: %%d >> "%LOGFILE%"
    )
)

echo Cleanup complete.
pause
endlocal
