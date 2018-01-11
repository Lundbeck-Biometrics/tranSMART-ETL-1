#!/bin/sh

#Approximate times on a small test system
#check numbers inserted each time

#KEGG 30 min ... most spent on search_keyword_term
./load-kegg.sh

#HMDB 40 min ... keywords and biomarkers together
./load-hmdb.sh

#MeSH 20 min
./load-mesh.sh

#Entrez gene-info 5 min
./load-entrez.sh

#MiRBase 2 min
./load-mirbase.sh

#UniProt SwissProt 50 min
./load-uniprot-sprot.sh

# Pathway.properties defines human only
#GO human 600 min
./load-go.sh
