# Loading dictionaries in tranSMART

This repo is based on tranSMART Foundation's release of tranSMART-ETL for tranSMART 16.2, 
and has been updated to only include the resources needed for loading the dictionary data
in tranSMART.

## Prerequisites

Apache Maven is required for the loading of dictionaries into tranSMART.

```
sudo apt-get install maven
```

Clone this repo on the server

```
cd /datastore
git clone https://github.com/Lundbeck-Biometrics/tranSMART-ETL-dictionaries
```

## Build

The command `mvn package` will build the dictionary loader package. 
It will create a subfolder named `target` containing the loader .jar files for loading the dictionary data.

```
cd /datastore/tranSMART-ETL-dictionaries
mvn package
```

## Get dictionaries

Download the dictionaries from the tranSMART Foundation website (http://library.transmartfoundation.org/dictionaries).

*For the SNP dictionary*: download the `reference-vcf-dictionary` archive.

```
cd /datastore/tranSMART-ETL-dictionaries
sudo wget http://library.transmartfoundation.org/dictionaries/reference-vcf-dictionary.tar.xz
sudo tar -xJf reference-vcf-dictionary.tar.xz
sudo chown -R transmart:transmart Data
mkdir dictionaries
mv Data/ dictionaries/Data
```

## Configure

Verify (and update if necessary) the configuration. Check if the path to the dictionary file is correct.

*For the SNP dictionary*: the configuration file is `VCF.properties`.

```
nano /datastore/tranSMART-ETL-dictionaries/load-dictionaries/conf/VCF.properties
```

## Load SNP dictionary

Use the scripts in the `load-dictionaries` folder to start the load.

*For the SNP dictionary*: the script is called `load-snp.sh`

```
cd /datastore/tranSMART-ETL-dictionaries/load-dictionaries
./load-snp.sh
```
