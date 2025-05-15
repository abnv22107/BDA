#!/bin/bash

# Set strict error handling
set -e

# Function to handle timeouts
timeout_handler() {
    echo "Execution timed out"
    exit 124
}

# Set up timeout handler
trap timeout_handler SIGTERM

# Compile if needed (for Scala/Java)
if [ -f "Main.scala" ]; then
    scalac Main.scala
elif [ -f "Main.java" ]; then
    javac Main.java
fi

# Run the program with input from stdin
if [ -f "Main.scala" ]; then
    scala Main
elif [ -f "Main.java" ]; then
    java Main
elif [ -f "Main.py" ]; then
    python3 Main.py
else
    echo "No valid source file found"
    exit 1
fi 