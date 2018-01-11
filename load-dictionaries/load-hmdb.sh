#!/bin/bash

touch hmdbdictionary.start

java -cp \
    /data/ETL/tranSMART-ETL/target/loader-jar-with-dependencies.jar \
    org.transmartproject.pipeline.dictionary/HMDBDictionary \
    /data/ETL/dictionaries/Data/HMDB/metabolite-dictionary.tsv \
    > hmdbdictionary.out 2> hmdbdictionary.err

touch hmdbdictionary.finish
