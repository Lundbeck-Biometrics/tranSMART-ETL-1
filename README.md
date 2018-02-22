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

## Loading a different dbSNP version than the one provided by the tranSMART Foundation

The instructions below assume dbSNP release 150 for hg38 will be loaded.

### Download dbSNP 150

Instead of downloading the dbSNP provided by the tranSMART Foundation, download the dbSNP release from ftp://ftp.ncbi.nlm.nih.gov/snp/organisms/human_9606/VCF/ (common_all_20170710.vcf.gz) and unarchive the file.

### Split the VCF into chunks

Because of the file size, we need to split it into chunks (and we do this based on the number of lines):
```
grep -v "^#" common_all.vcf > common.vcf
split -l 4000000 common.vcf common
```

### Disable indexes on database

Drop indexes from de_snp_info and de_rc_snp_info;

```
sudo su postgres
psql -d transmart

DROP INDEX deapp.de_snp_chrompos_ind;
DROP INDEX deapp.ind_vcf_rsid;
DROP INDEX deapp.ind_vcf_pos;
DROP INDEX deapp.de_rsnp_hgrs_ind;
DROP INDEX deapp.de_rsnp_chrompos_ind;
DROP INDEX deapp.de_rsnp_chrom_comp_idx;
DROP INDEX deapp.de_rc_snp_info_rs_id_idx;
DROP INDEX deapp.de_rc_snp_info_entrez_id_idx;
DROP INDEX deapp.de_rc_snp_info_chrom_pos_idx;
DROP INDEX deapp.de_r_s_i_ind4;

\q

exit
```

### Remove the code that does the COUNTs

Edit the file `VCF.groovy` and comment the queries that do the COUNTs to check whether a SNP already exists. We are assuming here that we know what we are doing and we won't load the same SNP twice. (see https://github.com/Lundbeck-Biometrics/tranSMART-ETL-dictionaries/commit/fbca6c763bf5500e3ad74ed941504a81282adefc)

Build the project with `mvn package`

### Configure and load

1. Edit the `VCF.properties` file

```
nano /datastore/tranSMART-ETL-dictionaries/load-dictionaries/conf/VCF.properties
```

Configure to skip the searchapp load:
```
# *****************************************************************
# if set to 'yes', vcf_table will not be re-created;
# otherwise it'll be re-created (drop it first if already exist)
# *****************************************************************
skip_create_vcf_table=no
skip_create_vcf_index=no

skip_create_vcf_gene_table=no
skip_create_vcf_gene_index=no

skip_process_vcf_data=no


# ***************************************************************************
# if set to 'yes', VCF data's REF and ALT columns will be ignored and only
# "chrom, rs_id, pos, variation class, and gene id/gene symbol" will by loaded
# from BROAD's VCF data for hg18 and hg19
# ***************************************************************************
skip_de_snp_info=no
skip_de_snp_gene_map=no
skip_de_rc_snp_info=no
skip_search_keyword=yes
skip_search_keyword_term=yes
```

2. Edit the `VCF.properties` file

```
nano /datastore/tranSMART-ETL-dictionaries/load-dictionaries/conf/VCF.properties
```

Update the path to the file to correspond to one of the chunks of the VCF file.

3. Perform step 2 for each of the chunks of the VCF file.

4. Edit the `VCF.properties` file

```
nano /datastore/tranSMART-ETL-dictionaries/load-dictionaries/conf/VCF.properties
```

Only load searchapp:
```
# *****************************************************************
# if set to 'yes', vcf_table will not be re-created;
# otherwise it'll be re-created (drop it first if already exist)
# *****************************************************************
skip_create_vcf_table=yes
skip_create_vcf_index=yes

skip_create_vcf_gene_table=yes
skip_create_vcf_gene_index=yes

skip_process_vcf_data=yes


# ***************************************************************************
# if set to 'yes', VCF data's REF and ALT columns will be ignored and only
# "chrom, rs_id, pos, variation class, and gene id/gene symbol" will by loaded
# from BROAD's VCF data for hg18 and hg19
# ***************************************************************************
skip_de_snp_info=yes
skip_de_snp_gene_map=yes
skip_de_rc_snp_info=yes
skip_search_keyword=no
skip_search_keyword_term=no
```


### Recreate indexes

Recreate the indexes for de_snp_info and de_rc_snp_info;

```
sudo su postgres
psql -d transmart

CREATE INDEX ind_vcf_rsid ON deapp.de_rc_snp_info USING btree (rs_id);
CREATE INDEX ind_vcf_pos ON deapp.de_rc_snp_info USING btree (pos);
CREATE UNIQUE INDEX de_rsnp_hgrs_ind ON deapp.de_rc_snp_info USING btree (hg_version, rs_id);
CREATE INDEX de_rsnp_chrompos_ind ON deapp.de_rc_snp_info USING btree (chrom, pos);
CREATE INDEX de_rsnp_chrom_comp_idx ON deapp.de_rc_snp_info USING btree (chrom, hg_version, pos);
CREATE INDEX de_rc_snp_info_rs_id_idx ON deapp.de_rc_snp_info USING btree (rs_id);
CREATE INDEX de_rc_snp_info_entrez_id_idx ON deapp.de_rc_snp_info USING btree (entrez_id);
CREATE INDEX de_rc_snp_info_chrom_pos_idx ON deapp.de_rc_snp_info USING btree (chrom, pos);
CREATE INDEX de_r_s_i_ind4 ON deapp.de_rc_snp_info USING btree (snp_info_id);
ALTER INDEX deapp.ind_vcf_rsid SET TABLESPACE indx;
ALTER INDEX deapp.ind_vcf_pos SET TABLESPACE indx;
ALTER INDEX deapp.de_rsnp_hgrs_ind SET TABLESPACE indx;
ALTER INDEX deapp.de_rsnp_chrompos_ind SET TABLESPACE indx;
ALTER INDEX deapp.de_rsnp_chrom_comp_idx SET TABLESPACE indx;
ALTER INDEX deapp.de_rc_snp_info_rs_id_idx SET TABLESPACE indx;
ALTER INDEX deapp.de_rc_snp_info_entrez_id_idx SET TABLESPACE indx;
ALTER INDEX deapp.de_rc_snp_info_chrom_pos_idx SET TABLESPACE indx;
ALTER INDEX deapp.de_r_s_i_ind4 SET TABLESPACE indx;

\q

exit
```
