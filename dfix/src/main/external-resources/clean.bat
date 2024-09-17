@del /Q .\logs\sessions\*.*
@del /Q .\logs\sim\sessions\*.*
@del /Q .\logs\*.*
@cd quick_fix
start /min cleanup.bat
exit