#!/bin/bash

# # uses conf/Pathway.properties kegg_*

touch kegg.start

java -cp \
    /data/ETL/tranSMART-ETL/target/loader-jar-with-dependencies.jar \
    org.transmartproject.pipeline.pathway/KEGG \
    > kegg.out 2> kegg.err

touch kegg.finish
