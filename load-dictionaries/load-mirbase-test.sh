#!/bin/bash

touch mirbasetest.start

java -cp \
    /data/ETL/tranSMART-ETL/target/loader-jar-with-dependencies.jar \
    org.transmartproject.pipeline.dictionary/MiRBaseDictionary \
    /data/ETL/dictionaries/Data/MiRBase/mirna-test.dat \
    /data/ETL/dictionaries/Data/MiRBase/aliases.txt \
    > mirbasetest.out 2> mirbasetest.err

touch mirbasetest.finish
