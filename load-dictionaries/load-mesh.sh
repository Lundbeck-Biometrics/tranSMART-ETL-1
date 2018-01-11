#!/bin/bash

# # uses conf/MeSH.properties to identify mesh_source
# # creates MeSH.tsv MeSH_Entry.tsv

touch mesh.start

java -cp \
    /data/ETL/tranSMART-ETL/target/loader-jar-with-dependencies.jar \
    org.transmartproject.pipeline.disease/MeSH \
    > mesh.out 2> mesh.err

touch mesh.finish
