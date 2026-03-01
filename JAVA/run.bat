@echo off
setlocal EnableDelayedExpansion

REM Java SpringBoot gRPC Client Build and Run Script for Windows

set JAVA_OPTS=-XX:+UseZGC -XX:+UnlockExperimentalVMOptions
set MAIN_CLASS=com.example.grpcclient.GrpcClientApplication

if "%~1"=="help" goto :usage
if "%~1"=="--help" goto :usage
if "%~1"=="-h" goto :usage
if "%~1"=="" goto :usage
if "%~1"=="build" goto :build
if "%~1"=="package" goto :package
if "%~1"=="run" goto :run
if "%~1"=="benchmark" goto :benchmark
if "%~1"=="clean" goto :clean
if "%~1"=="test" goto :test

echo Unknown option: %~1
goto :usage

:usage
echo Usage: %~0 [OPTION]
echo.
echo Options:
echo   build          Build the project
echo   run            Build and run the application  
echo   benchmark      Run performance benchmarks
echo   clean          Clean the project
echo   test           Run tests
echo   help           Show this help message
echo.
goto :end

:check_java
java -version >nul 2>&1
if errorlevel 1 (
  echo Java is not installed or not in PATH
  exit /b 1
)
goto :eof

:check_maven
mvn -version >nul 2>&1
if errorlevel 1 (
  echo Maven is not installed or not in PATH
  exit /b 1
)
goto :eof

:build
echo Building the project...
call :check_java
call :check_maven
mvn clean compile
if errorlevel 1 (
  echo Build failed
  exit /b 1
)
echo Build completed successfully!
goto :end

:package
echo Packaging the project...
call :check_java
call :check_maven
mvn clean package -DskipTests
if errorlevel 1 (
  echo Package failed
  exit /b 1
)
echo Package completed successfully!
goto :end

:run
echo Running the gRPC client application...
call :check_java
call :check_maven

if exist "target\grpc-client-1.0.0.jar" (
  echo Running packaged application...
  java %JAVA_OPTS% -jar target\grpc-client-1.0.0.jar
) else (
  echo Packaged jar not found. Building and running with Maven...
  mvn spring-boot:run -Dspring-boot.run.jvmArguments="%JAVA_OPTS%"
)
goto :end

:benchmark
echo Running performance benchmarks...
call :check_java
call :check_maven

if exist "target\grpc-client-1.0.0.jar" (
  java %JAVA_OPTS% -jar target\grpc-client-1.0.0.jar benchmark
) else (
  echo Packaged jar not found. Building first...
  call :package
  java %JAVA_OPTS% -jar target\grpc-client-1.0.0.jar benchmark
)
goto :end

:clean
echo Cleaning the project...
call :check_maven
mvn clean
if errorlevel 1 (
  echo Clean failed
  exit /b 1
)
echo Clean completed successfully!
goto :end

:test
echo Running tests...
call :check_maven
mvn test
if errorlevel 1 (
  echo Tests failed
  exit /b 1
)
echo Tests completed successfully!
goto :end

:end
endlocal