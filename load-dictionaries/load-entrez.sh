#!/bin/bash

# # uses conf/GeneInfo.properties gene_info

touch entrez.start

java -cp \
    /data/ETL/tranSMART-ETL/target/loader-jar-with-dependencies.jar \
    org.transmartproject.pipeline.annotation/GeneInfo \
    > entrez.out 2> entrez.err

touch entrez.finish
