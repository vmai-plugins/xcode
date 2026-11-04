@echo off
title X-Codes PC MCP Server
color 0b
echo =================================================================
echo  Launching X-Codes PC MCP Bridge...
echo =================================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\pc_mcp_server.ps1"
if errorlevel 1 (
    echo.
    echo Server exited with an error.
    pause
)
