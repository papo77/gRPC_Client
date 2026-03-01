#!/bin/bash

# Java SpringBoot gRPC Client Build and Run Script

set -e

JAVA_OPTS="-XX:+UseZGC -XX:+UnlockExperimentalVMOptions"
MAIN_CLASS="com.example.grpcclient.GrpcClientApplication"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m' 
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_usage() {
    echo -e "${BLUE}Usage: $0 [OPTION]${NC}"
    echo ""
    echo "Options:"
    echo "  build          Build the project"
    echo "  run            Build and run the application"
    echo "  benchmark      Run performance benchmarks"
    echo "  clean          Clean the project"
    echo "  test           Run tests"
    echo "  help           Show this help message"
    echo ""
}

check_java() {
    if ! command -v java &> /dev/null; then
        echo -e "${RED}Java is not installed or not in PATH${NC}"
        exit 1
    fi

    JAVA_VERSION=$(java -version 2>&1 | grep version | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt "21" ]; then
        echo -e "${YELLOW}Warning: Java 21+ is required. Current version: $JAVA_VERSION${NC}"
    fi
}

check_maven() {
    if ! command -v mvn &> /dev/null; then
        echo -e "${RED}Maven is not installed or not in PATH${NC}"
        exit 1
    fi
}

build_project() {
    echo -e "${BLUE}Building the project...${NC}"
    mvn clean compile
    echo -e "${GREEN}Build completed successfully!${NC}"
}

package_project() {
    echo -e "${BLUE}Packaging the project...${NC}"
    mvn clean package -DskipTests
    echo -e "${GREEN}Package completed successfully!${NC}"
}

run_application() {
    echo -e "${BLUE}Running the gRPC client application...${NC}"
    
    # Check if packaged jar exists
    if [ -f "target/grpc-client-1.0.0.jar" ]; then
        echo -e "${GREEN}Running packaged application...${NC}"
        java $JAVA_OPTS -jar target/grpc-client-1.0.0.jar
    else
        echo -e "${YELLOW}Packaged jar not found. Building and running with Maven...${NC}"
        mvn spring-boot:run -Dspring-boot.run.jvmArguments="$JAVA_OPTS"
    fi
}

run_benchmarks() {
    echo -e "${BLUE}Running performance benchmarks...${NC}"
    
    if [ -f "target/grpc-client-1.0.0.jar" ]; then
        java $JAVA_OPTS -jar target/grpc-client-1.0.0.jar benchmark  
    else
        echo -e "${YELLOW}Packaged jar not found. Building first...${NC}"
        package_project
        java $JAVA_OPTS -jar target/grpc-client-1.0.0.jar benchmark
    fi
}

clean_project() {
    echo -e "${BLUE}Cleaning the project...${NC}"
    mvn clean
    echo -e "${GREEN}Clean completed successfully!${NC}"
}

run_tests() {
    echo -e "${BLUE}Running tests...${NC}"
    mvn test
    echo -e "${GREEN}Tests completed successfully!${NC}"
}

# Check prerequisites
check_java
check_maven

# Main script logic
case "${1:-help}" in
    build)
        build_project
        ;;
    package)
        package_project
        ;;
    run)
        build_project
        run_application
        ;;
    benchmark)
        run_benchmarks
        ;;
    clean)
        clean_project
        ;;
    test)
        run_tests
        ;;
    help|--help|-h)
        print_usage
        ;;
    *)
        echo -e "${RED}Unknown option: $1${NC}"
        print_usage
        exit 1
        ;;
esac