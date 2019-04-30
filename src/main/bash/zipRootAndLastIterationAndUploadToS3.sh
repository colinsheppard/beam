#!/bin/bash

if [ "$#" -ne 2 ]; then
    echo "Illegal number of parameters";
    exit 1;
fi

filename="$1"
zipfile="$filename-it.$2"

zip -r "$zipfile.zip" $filename -x "$filename/ITERS/*"
zip -ur "$zipfile.zip" $filename/ITERS/it.$2

aws --region "us-east-2" s3 cp "$zipfile.zip" s3://beam-outputs/
echo "S3 URL: https://s3.us-east-2.amazonaws.com/beam-outputs/$zipfile.zip";
