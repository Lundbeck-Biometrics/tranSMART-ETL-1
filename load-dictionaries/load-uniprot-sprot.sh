#!/bin/bash

touch uniprotsprotdictionary.start

java -cp \
    /data/ETL/tranSMART-ETL/target/loader-jar-with-dependencies.jar \
    org.transmartproject.pipeline.dictionary/UniProtDictionary \
    /data/ETL/dictionaries/uniprot-sprot-dictionary.tsv \
    > uniprotsprotdictionary.out  2> uniprotsprotdictionary.err

touch uniprotsprotdictionary.finish
