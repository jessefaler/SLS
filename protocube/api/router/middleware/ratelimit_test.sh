#!/bin/bash

# Number of requests to send
COUNT=75

# Endpoint
URL="http://127.0.0.1:5050/api/blueprints"
# TEST KEY, Not an actual key
TOKEN="SLS_YesLGT_70skmR6ZeVx8juB1"

echo "Sending $COUNT requests to $URL..."

for i in $(seq 1 $COUNT); do
    echo "Request $i"
    curl -s -o /dev/null \
         -H "Authorization: Bearer $TOKEN" \
         -w "Status: %{http_code}\n" \
         "$URL"
done

echo "Done."
