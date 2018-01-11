#!/bin/sh

#Approximate times on a small test system
#check numbers inserted each time

#KEGG 30 min ... most spent on search_keyword_term
./load-kegg.sh

#HMDB 2 min ... keywords and biomarkers together (full 40 min)
./load-hmdb-test.sh

#MeSH 20 min
./load-mesh.sh

#Entrez gene-info 5 min
./load-entrez.sh

#MiRBase 2 min
./load-mirbase-test.sh

#UniProt Test 30 min (SwissProt 50 min)
./load-uniprot-test.sh

# Pathway.properties defines human only ... need a subset for tests
#GO human 600 min
#./load-go.sh
