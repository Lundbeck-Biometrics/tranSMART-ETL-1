#!/bin/bash

touch uniprotdictionary.start

java -cp \
    /data/ETL/tranSMART-ETL/target/loader-jar-with-dependencies.jar \
    org.transmartproject.pipeline.dictionary/UniProtDictionary \
    /data/ETL/dictionaries/Data/uniprot/uniprot-dictionary.tsv \
    > uniprotdictionary.out 2> uniprotdictionary.err

touch uniprotdictionary.finish
