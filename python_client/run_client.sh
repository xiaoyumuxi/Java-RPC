#!/bin/bash
cd "$(dirname "$0")"

if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv venv
    source venv/bin/activate
    pip install grpcio grpcio-tools protobuf
else
    source venv/bin/activate
fi

echo "Running Python Client..."
python3 client.py
