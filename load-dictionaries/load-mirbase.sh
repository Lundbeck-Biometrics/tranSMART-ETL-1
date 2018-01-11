#!/bin/bash

# Data from ftp://mirbase.org/pub/mirbase/21 latest release

touch mirbasedictionary.start

java -cp \
    /data/ETL/tranSMART-ETL/target/loader-jar-with-dependencies.jar \
    org.transmartproject.pipeline.dictionary/MiRBaseDictionary \
    /data/ETL/dictionaries/Data/MiRBase/miRNA.dat \
    /data/ETL/dictionaries/Data/MiRBase/aliases.txt \
    > mirbasedictionary.out 2> mirbasedictionary.err

touch mirbasedictionary.finish
