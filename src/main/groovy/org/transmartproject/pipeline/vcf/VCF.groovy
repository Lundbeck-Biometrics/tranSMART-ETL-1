/*************************************************************************
 * tranSMART - translational medicine data mart
 * 
 * Copyright 2008-2012 Janssen Research & Development, LLC.
 * 
 * This product includes software developed at Janssen Research & Development, LLC.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version, along with the following terms:
 *
 * 1.	You may convey a work based on this program in accordance with section 5,
 *      provided that you retain the above notices.
 * 2.	You may convey verbatim copies of this program code as you receive it,
 *      in any medium, provided that you retain the above notices.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * 
 *
 ******************************************************************/
  

package org.transmartproject.pipeline.vcf

import java.util.Properties

import org.transmartproject.pipeline.util.Util

import groovy.sql.Sql
import groovy.sql.GroovyRowResult
import org.apache.log4j.Logger
import org.apache.log4j.PropertyConfigurator
import org.transmartproject.pipeline.transmart.SearchKeyword
import org.transmartproject.pipeline.transmart.SearchKeywordTerm



class VCF {

	private static final Logger log = Logger.getLogger(VCF)

	private String genePairDelimiter, geneSymbolDelimiter
	private int batchSize

        private static SearchKeyword searchKeyword
        private static SearchKeywordTerm searchKeywordTerm
	
	static main(args) {

		PropertyConfigurator.configure("conf/log4j.properties");
		
		log.info("Start loading property file VCF.properties ...")
		Properties props = Util.loadConfiguration("conf/VCF.properties");

		Sql biomart = Util.createSqlFromPropertyFile(props, "biomart")
		Sql deapp = Util.createSqlFromPropertyFile(props, "deapp")
		Sql tm_lz = Util.createSqlFromPropertyFile(props, "tm_lz")
		Sql searchapp = Util.createSqlFromPropertyFile(props, "searchapp")

		searchKeyword = new SearchKeyword()
		searchKeyword.setSearchapp(searchapp)

		searchKeywordTerm = new SearchKeywordTerm()
		searchKeywordTerm.setSearchapp(searchapp)

		VCF vcf = new VCF()
		vcf.setGenePairDelimiter(props.get("gene_pair_delimiter"))
		vcf.setGeneSymbolDelimiter(props.get("gene_symbol_delimiter"))
		vcf.setBatchSize(Integer.parseInt(props.get("batch_size")))

		// Read VCF file and create two output files: .tsv and .gene
		// can be skipped by setting the property skip_process_vcf_data to yes
		vcf.processVCFData(props)

		// Create a table in tm_lz with the genome version in the name (tm_lz.vcf19)
		vcf.createVCFTable(tm_lz, props)
		// Load data from .tsv file into the table created in tm_lz (tm_lz.vcf19)
		vcf.loadVCFData(tm_lz, props)
		// Create index for the new table (tm_lz.vcf19)
		vcf.createVCFIndex(tm_lz, props)

		// Create another table in tm_lz with the genome version in the name and _gene (tm_lz.vcf19_gene)
		vcf.createVCFGeneTable(tm_lz, props)
		// Load data from .gene file into the _gene table in tm_lz (tm_lz.vcf19_gene)
		vcf.loadVCFGene(tm_lz, props)
		// Create index for the new table (tm_lz.vcf19_gene)
		vcf.createVCFGeneIndex(tm_lz, props)

		// Copy all rs_id, chrom, pos from tm_lz.vcf19 to deapp.de_snp_info
		// Note: it will not insert if the combination of rs_id, chrom, pos already exists
		// When we do the insert, we will also get a snp_info_id for each row (based on a trigger on the table
		// that gets the next unique id). This snp_info_id is then used in the load in deapp.de_rc_snp_info
		// and searchapp.search_keyword that follow.
		vcf.loadDeSnpInfo(deapp, tm_lz, props)

		// Nothing happens here for Postgres
		// So actually the creation of the .gene file and the _gene table is not needed for Postgres
		// and we could safely set skip_create_vcf_gene_table=yes and skip_create_vcf_gene_index=yes
		vcf.loadDeSnpGeneMap(deapp, tm_lz, props)

		// Copy from tm_lz.vcf19 to deapp.de_rc_snp_info
		// Note: it will not insert if the combination of snp_info_id, hgversion, and rs_id already exists
		vcf.loadDeRcSnpInfo(deapp, tm_lz, props)

		// Copy rs_id and snp_info_id from deapp.de_snp_info into searchapp.search_keyword and then
		// the rs_id and a unique searchkeyword id (generated when inserting into search_keyword)
		// into searchapp.search_keyword_term
		vcf.loadSearchKeyword(searchapp, deapp, props)

		searchKeyword.closeSearchKeyword()
		searchKeywordTerm.closeSearchKeywordTerm()

		print new Date()
		println" VCF SNPs load completed successfully"
	}


	void loadDeRcSnpInfo(Sql deapp, Sql tmlz, Properties props){

            Boolean isPostgres = Util.isPostgres()
            String qry1;
            String qry2;
            String qry3;
            String qry4;
			String hgVersion = props.get("human_genome_version")
			String dbSNPVersion = props.get("dbSNP_version")
            String vcfTable = "vcf" + hgVersion

            if(props.get("skip_de_rc_snp_info").toString().toLowerCase().equals("yes")){
                log.info("Skip loading VCF's SNP RS# from table $vcfTable into DE_RC_SNP_INFO ...")
            }else{
                log.info("Start loading VCF's SNP RS# from $vcfTable into DE_RC_SNP_INFO ...")

                if (isPostgres) {
                    qry1 = "select rs_id,chrom, pos, ref, alt, gene_info, variation_class from tm_lz.$vcfTable "
                    qry2 = "select count(*) from deapp.de_rc_snp_info where snp_info_id = ? and rs_id = ? and hg_version = $hgVersion and dbsnp_version = $dbSNPVersion"
                    qry3 = """ insert into DE_RC_SNP_INFO (snp_info_id, rs_id, chrom, pos, ref, alt, gene_info, gene_name, entrez_id, variation_class, hg_version, dbsnp_version)
                                                         values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, $hgVersion, $dbSNPVersion)
			   """
                    qry4 = "select snp_info_id from de_snp_info where name = ? and hg_version = $hgVersion and dbsnp_version = $dbSNPVersion"
                } else {
                    qry1 = "select rs_id,chrom, pos, ref, alt, gene_info, variation_class from tm_lz.$vcfTable "
                    qry2 = "select count(*) from deapp.de_rc_snp_info where snp_info_id = ? and rs_id = ? and hg_version = $hgVersion and dbsnp_version = $dbSNPVersion"
                    qry3 = """ insert into DE_RC_SNP_INFO (snp_info_id, rs_id, chrom, pos, ref, alt, gene_info, gene_name, entrez_id, variation_class, hg_version, dbsnp_version)
                                                         values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, $hgVersion, $dbSNPVersion)
						 """
                    qry4 = "select snp_info_id from de_snp_info where name = ? and hg_version = $hgVersion and dbsnp_version = $dbSNPVersion"
		}

                //deapp.withTransaction {
                    deapp.withBatch(batchSize,qry3, { ps ->
                        tmlz.eachRow(qry1) {
                            GroovyRowResult rowResult = deapp.firstRow(qry4, [it.rs_id])
                            int snpInfoId = rowResult[0]
                            rowResult = deapp.firstRow(qry2, [snpInfoId, it.rs_id])
                            int count = rowResult[0]
                            if(count > 0){
                                log.info "${snpInfoId}:${it.rs_id}:${hgVersion}:${dbSNPVersion} already exists in DE_RC_SNP_INFO ..."
                            }
                            else{
                                if(it.gene_info == null || it.gene_info.indexOf(":") < 0) {
                                    log.info "Insert ${it.rs_id}:${it.chrom}:${it.pos} ${snpInfoId} '' into DE_RC_SNP_INFO ..."
                                    ps.addBatch([snpInfoId, it.rs_id, it.chrom, it.pos, it.ref, it.alt,
                                                 it.gene_info, null, null, it.variation_class])
                                } else {
                                    String[] genes = it.gene_info.split(/(:|\|)/)
                                    String geneName = genes[0]
                                    long geneId = genes[1].toInteger()
                                    log.info "Insert ${it.rs_id}:${it.chrom}:${it.pos} ${snpInfoId} ${geneName}:${geneId} into DE_RC_SNP_INFO ..."
                                    ps.addBatch([snpInfoId, it.rs_id, it.chrom, it.pos, it.ref, it.alt,
                                                 it.gene_info, geneName, geneId, it.variation_class])
                                }
                            }
                        }
                    })
                //}

                log.info("End loading VCF's SNP RS# from $vcfTable into DE_RC_SNP_INFO ...")
            }
	}


        void loadDeSnpGeneMap(Sql deapp, Sql tmlz, Properties props){

            Boolean isPostgres = Util.isPostgres()
            String qry1;
            String qry2;
            String qry3;
            String qry4;
            String vcfGeneTable = "vcf" + props.get("human_genome_version") + "_gene"
            if(isPostgres) {
                log.info("On Postgres DE_SNP_GENE_MAP is a view, no need to load copy of data ...")
            }
            else {
    
                if(props.get("skip_de_snp_gene_map").toString().toLowerCase().equals("yes")){
                    log.info("Skip loading VCF's SNP RS# from table $vcfGeneTable into DE_SNP_GENE_MAP ...")
                }else{
                    log.info("Start loading VCF's SNP RS# from $vcfGeneTable into DE_SNP_GENE_MAP ...")

                    if(isPostgres) {
                        qry1 = "select rs_id, gene_id from tm_lz.$vcfGeneTable"
                        qry2 = "select count(*) from de_snp_gene_map where snp_id = ? and snp_name=? and entrez_gene_id=?"
                        qry3 = """ insert into DE_SNP_GENE_MAP (snp_id, snp_name, entrez_gene_id)
							 values (?, ?, ?)
						 """
                        qry4 = "select snp_info_id from deapp.de_snp_info where name = ?"
                    } else {
                        qry1 = "select rs_id, gene_id from tm_lz.$vcfGeneTable"
                        qry2 = "select count(*) from de_snp_gene_map where snp_id = ? and snp_name=? and entrez_gene_id=?"
                        qry3 = """ insert into DE_SNP_GENE_MAP (snp_id, snp_name, entrez_gene_id)
							 values (?, ?, ?)
						 """
                        qry4 = "select snp_info_id from deapp.de_snp_info where name = ?"
                    }

                    //deapp.withTransaction {
                        deapp.withBatch(batchSize,qry3, { ps ->
                            tmlz.eachRow(qry1) {
                                GroovyRowResult rowResult = deapp.firstRow(qry4, [it.rs_id])
                                int snpInfoId = rowResult[0]
                                rowResult = deapp.firstRow(qry2, [snpInfoId, it.rs_id, it.gene_id])
                                int count = rowResult[0]
                                if(count > 0){
                                    log.info "${it.rs_id}:${it.gene_id} already exists in DE_SNP_GENE_MAP ..."
                                }
                                else{
                                    log.info "Insert ${it.rs_id}:${it.gene_id} ${snpInfoId} into DE_SNP_GENE_MAP ..."
                                    ps.addBatch([snpInfoId, it.rs_id, it.gene_id])
                                }
                            }
                        })
                    //}
                
                    log.info("End loading VCF's SNP RS# from $vcfGeneTable into DE_SNP_GENE_MAP ...")
                }
            }
	}


        void loadDeSnpInfo(Sql deapp, Sql tmlz, Properties props){

            Boolean isPostgres = Util.isPostgres()
            String qry1;
            String qry2;
            String qry3;
			String hgVersion = props.get("human_genome_version")
			String dbSNPVersion = props.get("dbSNP_version")
            String vcfTable = "vcf" + hgVersion


            if(props.get("skip_de_snp_info").toString().toLowerCase().equals("yes")){
                log.info("Skip loading VCF's SNP RS# from table $vcfTable into DE_SNP_INFO ...")
            }else{
                log.info("Start loading VCF's SNP RS# from table $vcfTable into DE_SNP_INFO ...")

                if(isPostgres) {
                    qry1 = "select rs_id, chrom, pos from tm_lz.$vcfTable"
                    qry2 = "select count(*) from de_snp_info where name=? and chrom=? and chrom_pos=? and hg_version=$hgVersion and dbsnp_version=$dbSNPVersion"
                    qry3 = "insert into DE_SNP_INFO (name, chrom, chrom_pos, hg_version, dbsnp_version) values (?, ?, ?, $hgVersion, $dbSNPVersion)"
                } else {
                    qry1 = "select rs_id, chrom, pos from tm_lz.$vcfTable"
                    qry2 = "select count(*) from de_snp_info where name=? and chrom=? and chrom_pos=? and hg_version=$hgVersion and dbsnp_version=$dbSNPVersion"
                    qry3 = "insert into DE_SNP_INFO (name, chrom, chrom_pos, hg_version, dbsnp_version) values (?, ?, ?, $hgVersion, $dbSNPVersion)"
                }
                
                //deapp.withTransaction {
                    deapp.withBatch(batchSize,qry3, { ps ->
                        tmlz.eachRow(qry1) {
                            GroovyRowResult rowResult = deapp.firstRow(qry2, [it.rs_id, it.chrom, it.pos])
                            int count = rowResult[0]
                            if(count > 0){
                                log.info "${it.rs_id}:${it.chrom}:${it.pos}:${hgVersion}:${dbSNPVersion} already exists in DE_SNP_INFO ..."
                            }
                            else{
                                log.info "Insert ${it.rs_id}:${it.chrom}:${it.pos}:${hgVersion}:${dbSNPVersion} into DE_SNP_INFO ..."
                                ps.addBatch([it.rs_id, it.chrom, it.pos])
                            }
                        }

                    })
                    //}
                
                log.info("End loading VCF's SNP RS# from table $vcfTable into DE_SNP_INFO ...")
            }
	}


    void loadSearchKeyword(Sql searchapp, Sql deapp, Properties props){

            Boolean isPostgres = Util.isPostgres()
            String qry;
            String qrysyn;
			String hgVersion = props.get("human_genome_version")
			String dbSNPVersion = props.get("dbSNP_version")

            if(props.get("skip_search_keyword").toString().toLowerCase().equals("yes")){
                log.info("Skip loading SNP RS# from DE_SNP_INFO to SEARCH_KEYWORD ...")
            }else{
                log.info("Start loading SNP RS# from DE_SNP_INFO to SEARCH_KEYWORD ...")

                if(isPostgres) {
    			//String stmt = "alter table search_keyword disable constraint "
			//searchapp.execute(stmt)

                        qry = " select name, snp_info_id from deapp.de_snp_info "
                } else {
    			//String stmt = "alter table search_keyword disable constraint "
			//searchapp.execute(stmt)

                        qry = " select name, snp_info_id from deapp.de_snp_info "
                }

                deapp.eachRow(qry)
                {
                    long snpInfoId = it.snp_info_id
                    searchKeyword.insertSearchKeyword(it.name, snpInfoId,
                                                              'SNP:'+hgVersion+':'+dbSNPVersion+':'+it.name,
                                                              'SNP', 'SNP', 'SNP')
                    long searchKeywordID = searchKeyword.getSearchKeywordId(it.name, 'SNP')
                    if(searchKeywordID){
                        searchKeywordTerm.insertSearchKeywordTerm(it.name, searchKeywordID, 1)
                    }
                }
                
                log.info("End loading SNP RS# from table DE_SNP_INFO into SEARCH_KEYWORD ...")
            }
	}


	void loadVCFData(Sql biomart, Properties props){

		int index = 0
		String vcfTable = "vcf" + props.get("human_genome_version")

		File vcfData = new File(props.get("vcf_source_file") + ".tsv")

		String qry = """ insert into $vcfTable (chrom, pos, rs_id, ref, alt, variation_class,
                                gene_info, af, gmaf)
						 values(?, ?, ?, ?, ?,  ?, ?, ?, ?) """

		if(vcfData.size() > 0){
			log.info("Start loading VCF data from the file [${vcfData.toString()}] into the table ${vcfTable} ...")
			//biomart.withTransaction {
			biomart.withBatch(batchSize, qry, { stmt ->
				vcfData.eachLine {
					index++
					if((index % 100000) == 0) {
						if((index % 1000000) == 0) println index + "..."
						else print index + "..."
					}

					String [] str = it.split("\t")

					if(str.size() > 8) {
						stmt.addBatch([
							str[0].replace("chr", ""),
							str[1],
							str[2],
							str[3],
							str[4],
							str[5],
							str[6],
							str[7],
							str[8]
						])
					}else if(str.size() > 7) {
						stmt.addBatch([
							str[0].replace("chr", ""),
							str[1],
							str[2],
							str[3],
							str[4],
							str[5],
							str[6],
							str[7],
							null
						])
					}else if(str.size() > 6) {
						stmt.addBatch([
							str[0].replace("chr", ""),
							str[1],
							str[2],
							str[3],
							str[4],
							str[5],
							str[6],
							null,
							null
						])
					} else {
						stmt.addBatch([
							str[0].replace("chr", ""),
							str[1],
							str[2],
							str[3],
							str[4],
							str[5],
							null,
							null,
							null
						])
					}
				}
			})
			//}
			log.info("End loading VCF data into ${vcfTable} ...")
		}else{
			log.error("File ${vcfData.toString()} is empty or does not exist ...")
		}
	}


	void loadVCFGene(Sql biomart, Properties props){

		String vcfGeneTable = "vcf" + props.get("human_genome_version") + "_gene"

		String qry = """ insert into $vcfGeneTable (chr, rs_id, pos, gene_symbol, gene_id) values(?, ?, ?, ?, ?) """

		File input = new File(props.get("vcf_source_file") + ".gene")
		if(input.size() > 0){
			log.info("Start loading VCF data into ${vcfGeneTable} ...")
			//biomart.withTransaction {
			biomart.withBatch(batchSize, qry, { stmt ->
				input.eachLine{
					def str = it.split("\t")
					Map genePair = getGeneMap(str[3])
					genePair.each{ k, v ->
						stmt.addBatch([str[0], str[2], str[1], k, v])
					}
				}
			})
			//}
		} else{
			log.info(input.toString() + " is empty or does not exist ...")
		}
	}


	void processVCFData(Properties props){
		if(props.get("skip_process_vcf_data").toString().toLowerCase().equals("yes")){
			log.info("Skip processing VCF data: ${props.get("vcf_source_file")} ...")
		}else{
			File input = new File(props.get("vcf_source_file"))

			File output = new File(props.get("vcf_source_file") + ".tsv")
			if(output.size() > 0){
				output.delete()
			}
			output.createNewFile()

			File geneOutput = new File(props.get("vcf_source_file") + ".gene")
			if(geneOutput.size() > 0){
				geneOutput.delete()
			}
			geneOutput.createNewFile()

			log.info("Start processing VCF data: " + input.toString() + "...")
			readVCFData1(input, output, geneOutput, props)
			log.info("End processing VCF data: " + input.toString() + "...")
		}
	}


	// not used anymore
	void readVCFData(File vcfInput, File output, File geneOutput, Properties props){

		String [] str, info
		StringBuffer sb = new StringBuffer()
		StringBuffer line = new StringBuffer()

		StringBuffer sb1 = new StringBuffer()
		StringBuffer line1 = new StringBuffer()

		String vc, geneinfo
		Map infoId = [:]

		String [] infoIdList = props.get("info_id_list").split(";")

		int lineNum = 0
		if(vcfInput.size() > 0){
			vcfInput.eachLine {
				vc = ""
				geneinfo = ""


				if(it.indexOf("#") != 0) {
					lineNum++
					str = it.split("\t")

					// VCF v4.1 columns: CHROM, POS, ID, REF, ALT, QUAL, FILTER, INFO
					sb.append("${str[0]}\t${str[1]}\t${str[2]}\t${str[3]}\t${str[4]}\t")

					if(str[7].indexOf(";") != -1) {
						info = str[7].split(";")
						info.each{ id ->
							if(id.indexOf("VC=") != -1) vc = id.replace("VC=", "").trim()

							if(id.indexOf("GENEINFO=") != -1) {
								geneinfo = id.replace("GENEINFO=", "").trim()
								line.append(str[2] + "\t" + geneinfo + "\n")
							}

							infoIdList.each{
								if(id.indexOf(it + "=") == 0){
									infoId[it] = id.replace(it + "=", "").trim()
								}
							}
						}
					}

					sb.append(vc + "\t")
					sb.append(geneinfo + "\t")
					infoIdList.each{
						if(!infoId[it].equals(null) && infoId[it].size() > 0) {
							sb.append(infoId[it] + "\t")
						} else {
							sb.append("\t")
						}
					}
					sb.append("\n")

					if((lineNum % 100000) == 0) {
						if((lineNum % 1000000) == 0) println lineNum + "..."
						else print lineNum + "..."
						output.append(sb.toString())
						sb.delete(0, sb.size())

						geneOutput.append(line.toString())
						line.delete(0, line.size())
					}
				}
			}

			println lineNum
			log.info("Total SNP# in " + vcfInput.toString() + ": \t" + lineNum)

			output.append(sb.toString())
			sb.delete(0, sb.size())

			geneOutput.append(line.toString())
			line.delete(0, line.size())
		}else{
			log.error("The file " + vcfInput.toString() + " is empty or does not exist ...")
		}
	}


	void readVCFData1(File vcfInput, File output, File geneOutput, Properties props){

		StringBuffer [] sb = new StringBuffer()[]
		StringBuffer line = new StringBuffer()
		StringBuffer gene = new StringBuffer()

		String [] infoIdList = props.get("info_id_list").split(";")

		int lineNum = 0
		if(vcfInput.size() > 0){
			vcfInput.eachLine {
				if((it.indexOf("##") == -1) && (it.indexOf("chr") == 0)){
					lineNum++

					sb = readLine(it, infoIdList)
					line.append(sb[0].toString())
					gene.append(sb[1].toString())

					if((lineNum % 100000) == 0) {
						if((lineNum % 1000000) == 0) println lineNum + "..."
						else print lineNum + "..."
						output.append(line.toString())
						line.setLength(0)

						geneOutput.append(gene.toString())
						gene.setLength(0)
					}
				}
			}

			println lineNum
			log.info("Total SNP# in " + vcfInput.toString() + ": \t" + lineNum)

			output.append(line.toString())
			line.setLength(0)

			geneOutput.append(gene.toString())
			gene.setLength(0)
		}else{
			log.error("The file " + vcfInput.toString() + " is empty or does not exist ...")
		}
	}


	StringBuffer [] readLine(String line, String [] infoIdList){

		StringBuffer sb = new StringBuffer()
		StringBuffer gene = new StringBuffer()
		ArrayList rsid = new ArrayList()

		// VCF v4.1 columns: CHROM, POS, ID, REF, ALT, QUAL, FILTER, INFO
		String [] str = line.split("\t")

		String vc =getAttributeValue("VC", str[7])
		String geneInfo = getAttributeValue("GENEINFO", str[7])

		// split rs_id if there are multiple
		rsid = getSnpId(str[2])
		rsid.each{
			// generate a record for VCF table
			sb.append("${str[0]}\t${str[1]}\t$it\t${str[3]}\t${str[4]}\t")
			sb.append( vc + "\t")
			sb.append(geneInfo + "\t")

			infoIdList.each{
				sb.append(getAttributeValue(it, str[7]) + "\t")
			}
			sb.append("\n")

			// generate a record for VCF_GENE table
			if(geneInfo.size() > 0) gene.append("${str[0]}\t${str[1]}\t$it\t$geneInfo\n")
		}
		
		return [sb, gene]
	}



	ArrayList getSnpId(String ids){
		ArrayList rsid = new ArrayList()
		if(ids.indexOf(";") != -1){
			String [] str = ids.split(";")
			str.each{ rsid.add(it) }
		} else {
			rsid.add(ids)
		}

		return rsid
	}


	/**
	 * 
	 * @param attributeName		attribute name, usaullay in upper case
	 * @param info				value of INFO column
	 * @return					extracted value for this attribute
	 */
	String getAttributeValue(String attributeName, String info){

		String value = ""
		String [] str = info.split(";")
		str.each{
			if(it.indexOf(attributeName + "=") != -1)  value = it.replace(attributeName + "=", "").trim()
		}

		return value
	}


	/**
	 *  extract [gene symbol:gene id] map to VCF_GENE and 
	 *  later populate DE_SNP_GENE_MAP
	 *  
	 * @param str	value from INFO's GENEINFO attribute
	 * @return		a map: gene_symbol -> gene_id
	 */
	Map getGeneMap(String str){

		Map geneMap = [:]
		String [] genes, lst

		if(str.indexOf(genePairDelimiter) != -1){

			/* The gene pair's delimiter "|" cannot escaped from a parameter, 
			 * so hardcode it here for now.
			 */
			genes = str.split(/\|/)
			genes.each{
				lst = it.split(geneSymbolDelimiter)
				geneMap[lst[0]] = lst[1]
			}
		}else{
			lst = str.split(geneSymbolDelimiter)
			geneMap[lst[0]] = lst[1]
		}

		return geneMap
	}



	/**
	 *  create index after data is loaded for performance reason
	 *  
	 * @param sql
	 * @param props
	 */
        void createVCFIndex(Sql sql, Properties props){

            Boolean isPostgres = Util.isPostgres()
            String qry;
            String qry1;
            String vcfTable = "vcf" + props.get("human_genome_version")

            if(props.get("skip_create_vcf_index").toString().toLowerCase().equals("yes")){
                log.info("Skip creating indexes on table ${vcfTable} ...")
            }else{

                log.info "Start creating indexes for table: ${vcfTable}"

                if(isPostgres) {
                    qry = """ create index idx_${vcfTable} on ${vcfTable} (rs_id)"""   /* tablespace indx */

                    qry1 = "select count(*)  from pg_indexes where indexname=?"
                    if(sql.firstRow(qry1, [vcfTable])[0] > 0){
                        qry1 = "drop index ${vcfTable}"
                        sql.execute(qry1)
                    }
                } else {
                    qry = """ create index idx_${vcfTable} on ${vcfTable} (rs_id) nologging parallel tablespace indx """

                    qry1 = "select count(*)  from user_indexes where index_name=?"
                    if(sql.firstRow(qry1, [vcfTable.toUpperCase()])[0] > 0){
                        qry1 = "drop index ${vcfTable} purge"
                        sql.execute(qry1)
                    }
                }
            
                sql.execute(qry)

                log.info "End creating indexes for table: ${vcfTable}"
            }
        }


	/**
	 *  create index after data is loaded for performance reason
	 *
	 * @param sql
	 * @param props
	 */
	void createVCFGeneIndex(Sql sql, Properties props){

            Boolean isPostgres = Util.isPostgres()
            String qry;
            String qry1;
            String vcfGeneTable = "vcf" + props.get("human_genome_version") + "_gene"

            if(props.get("skip_create_vcf_gene_index").toString().toLowerCase().equals("yes")){
                log.info("Skip creating indexes on table ${vcfGeneTable} ...")
            }else{

                log.info "Start creating indexes for table: ${vcfGeneTable}"

                if (isPostgres) {
                    qry = """ create index idx_${vcfGeneTable} on ${vcfGeneTable} (rs_id) """ /* tablespace indx */

                    qry1 = "select count(*)  from pg_indexes where indexname=?"
                    if(sql.firstRow(qry1, [vcfGeneTable])[0] > 0){
                        qry1 = "drop index ${vcfGeneTable}"
                        sql.execute(qry1)
                    }
                } else  {
                    qry = """ create index idx_${vcfGeneTable} on ${vcfGeneTable} (rs_id) nologging parallel tablespace indx """

                    qry1 = "select count(*)  from user_indexes where index_name=?"
                    if(sql.firstRow(qry1, [vcfGeneTable.toUpperCase()])[0] > 0){
                        qry1 = "drop index ${vcfGeneTable} purge"
                        sql.execute(qry1)
                    }
                }
                
                sql.execute(qry)

                log.info "End creating indexes for table: ${vcfGeneTable}"
            }
	}


	/**
	 *  create table without index for performance reason
	 *
	 * @param sql
	 * @param props
	 */
	void createVCFTable(Sql sql, Properties props){

            Boolean isPostgres = Util.isPostgres()
            String qry;
            String qry1;
            String vcfTable = "vcf" + props.get("human_genome_version")
            String [] ids = props.get("info_id_list").split(";")
            String str = ""

            if(props.get("skip_create_vcf_table").toString().toLowerCase().equals("yes")){
                log.info("Skip creating table: ${vcfTable} ...")
            }else{
                log.info "Start creating table: ${vcfTable}"

                if(isPostgres){
                    ids.each{ str += "  $it  varchar(1000), \n" }
                    qry = """ create table ${vcfTable} (
					chrom  			varchar(2),
					pos			numeric(10),
					rs_id			varchar(200),
					ref			varchar(4000),
					alt			varchar(4000),
					variation_class		varchar(10),
					$str
					gene_info		varchar(1000)
				)
                    """
                    qry1 = "select count(*)  from pg_tables where tablename=?"
                    if(sql.firstRow(qry1, [vcfTable])[0] > 0){
                        qry1 = "drop table ${vcfTable}"
                        sql.execute(qry1)
                    }
                } else {
                    ids.each{ str += "  $it  varchar2(1000), \n" }
                    qry = """ create table ${vcfTable} (
					chrom  			varchar2(2),
					pos			number(10),
					rs_id			varchar2(200),
					ref			varchar2(4000),
					alt			varchar2(4000),
					variation_class		varchar2(10),
					$str
					gene_info		varchar2(1000)
				)
				partition by list (chrom)
				(
					partition part_chr1 values('1'),
					partition part_chr2 values('2'),
					partition part_chr3 values('3'),
					partition part_chr4 values('4'),
					partition part_chr5 values('5'),
					partition part_chr6 values('6'),
					partition part_chr7 values('7'),
					partition part_chr8 values('8'),
					partition part_chr9 values('9'),
					partition part_chr10 values('10'),
					partition part_chr11 values('11'),
					partition part_chr12 values('12'),
					partition part_chr13 values('13'),
					partition part_chr14 values('14'),
					partition part_chr15 values('15'),
					partition part_chr16 values('16'),
					partition part_chr17 values('17'),
					partition part_chr18 values('18'),
					partition part_chr19 values('19'),
					partition part_chr20 values('20'),
					partition part_chr21 values('21'),
					partition part_chr22 values('22'),
					partition part_chrX values('X'),
					partition part_chrY values('Y'),
					partition part_chrM values('M'),
					partition part_other values(default)
				) nologging
			   """

                    qry1 = "select count(*)  from user_tables where table_name=?"
                    if(sql.firstRow(qry1, [vcfTable.toUpperCase()])[0] > 0){
                        qry1 = "drop table ${vcfTable} purge"
                        sql.execute(qry1)
                    }
                }
                
                sql.execute(qry)

                log.info "End creating table: ${vcfTable}"
            }
	}


	/**
	 *  create table without index for performance reason
	 *
	 * @param sql
	 * @param props
	 */
	void createVCFGeneTable(Sql sql, Properties props){

            Boolean isPostgres = Util.isPostgres()
            String qry;
            String qry1;
            String vcfGeneTable = "vcf" + props.get("human_genome_version") + "_gene"

            if(props.get("skip_create_vcf_gene_table").toString().toLowerCase().equals("yes")){
                log.info("Skip creating table: ${vcfGeneTable} ...")
            }else{
                log.info "Start creating table: ${vcfGeneTable}"

                if(isPostgres) {
                    qry = """ create table ${vcfGeneTable} (
					chr			varchar(10),
					pos			numeric(10),
					rs_id			varchar(200),
					gene_symbol		varchar(100),
					gene_id			numeric(10)
					)
			  """

                    qry1 = "select count(*)  from pg_tables where tablename=?"
                    if(sql.firstRow(qry1, [vcfGeneTable])[0] > 0){
                        qry1 = "drop table ${vcfGeneTable}"
                        sql.execute(qry1)
                    }
                } else {
    
                    qry = """ create table ${vcfGeneTable} (
					chr			varchar2(10),
					pos			number(10),
					rs_id			varchar2(200),
					gene_symbol		varchar2(100),
					gene_id			number(10)
					)
			  """

                    qry1 = "select count(*)  from user_tables where table_name=?"
                    if(sql.firstRow(qry1, [vcfGeneTable.toUpperCase()])[0] > 0){
                        qry1 = "drop table ${vcfGeneTable} purge"
                        sql.execute(qry1)
                    }
                }

                sql.execute(qry)

			log.info "End creating table: ${vcfGeneTable}"
		}
	}


	void setGeneSymbolDelimiter( String geneSymbolDelimiter){
		this.geneSymbolDelimiter = geneSymbolDelimiter
	}


	void setGenePairDelimiter(String genePairDelimiter){
		this.genePairDelimiter = genePairDelimiter
	}


	void setBatchSize(int batchSize){
		this.batchSize = batchSize
	}
	
}
