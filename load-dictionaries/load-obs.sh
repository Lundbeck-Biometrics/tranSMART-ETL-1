#!/bin/bash

# uses conf/Observation.properties to identify input file

touch observation.start

java -cp \
    /data/ETL/tranSMART-ETL/target/loader-jar-with-dependencies.jar \
    org.transmartproject.pipeline.observation/Observation \
    > observation.out 2> observation.err

touch observation.finish
