#!/bin/bash

# Infinite loop
while true
do
    python stream-producer.py 5000 100 
    sleep 10  # Delay for 10 second (adjust as needed)
done
