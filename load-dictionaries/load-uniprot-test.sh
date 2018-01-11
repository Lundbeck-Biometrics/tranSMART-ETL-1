#!/bin/bash

touch uniprottest.start

java -cp \
    /data/ETL/tranSMART-ETL/target/loader-jar-with-dependencies.jar \
    org.transmartproject.pipeline.dictionary/UniProtDictionary \
    /data/ETL/dictionaries/uniprot-test.tsv \
    > uniprottest.out  2> uniprottest.err

touch uniprottest.finish
