#!/bin/bash

# # uses conf/Pathway.properties gene_ontology_* gene_association_*
# #  and pathway_* pointing to gene_ontology
touch geneontology.start

java -cp \
    /data/ETL/tranSMART-ETL/target/loader-jar-with-dependencies.jar \
    org.transmartproject.pipeline.pathway/GeneOntology \
    > geneontology.out 2> geneontology.err

touch geneontology.finish
