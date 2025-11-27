#!/bin/bash

# Build the binary
if go build -o sls .; then
    # If build succeeds, clear the terminal
    clear
    # Run the binary
    ./sls
else
    echo "Build failed!"
fi