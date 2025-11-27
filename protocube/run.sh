#!/bin/bash

# Build the binary
if go build -o protocube .; then
    # If build succeeds, clear the terminal
    clear
    # Run the binary
    ./protocube
else
    echo "Build failed!"
fi