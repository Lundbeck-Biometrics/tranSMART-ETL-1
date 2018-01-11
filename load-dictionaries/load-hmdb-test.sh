#!/bin/bash

touch hmdbtest.start

java -cp \
    /data/ETL/tranSMART-ETL/target/loader-jar-with-dependencies.jar \
    org.transmartproject.pipeline.dictionary/HMDBDictionary \
    /data/ETL/dictionaries/metabolite-test.tsv \
    > hmdbtest.out 2> hmdbtest.err

touch hmdbtest.finish
