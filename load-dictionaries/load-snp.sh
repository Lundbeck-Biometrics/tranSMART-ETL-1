#!/bin/bash

# uses conf/Observation.properties to identify input file

touch snp.start

java -cp \
    /datastore/tranSMART-ETL/target/loader-jar-with-dependencies.jar \
    org.transmartproject.pipeline.vcf/VCF \
    > snp.out 2> snp.err

touch snp.finish
