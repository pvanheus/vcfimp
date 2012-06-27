package ca.innovativemedicine.vcf.parsers

import ca.innovativemedicine.vcf._

import scala.util.parsing.combinator.JavaTokenParsers


/**
 * Provides parsers for genotype data; that is, the FORMAT strings and the
 * sample data (sample data parser generated from the FORMAT string).
 */
trait GenotypeParsers extends JavaTokenParsers with VcfValueParsers {
  import Metadata._
  
  def vcfInfo: VcfInfo
  
  
  /**
   * Parses a FORMAT field, followed by exactly `genotypeCount` genotypes.
   * 
   * The order of the formats and the order of the values in the genotype
   * fields must exactly.
   */
  def genotypes(genotypeCount: Int, alleleCount: Int): Parser[(List[Format], List[List[List[VcfValue]]])] =
    format >> { fmts =>
      repN(genotypeCount, genotype(fmts, genotypeCount, alleleCount)) ^^ { fmts -> _ }
    }
  
  
  def genotype(formats: List[Format], genotypeCount: Int, alleleCount: Int): Parser[List[List[VcfValue]]] = {
    val parsers = formats map (getParser(_, genotypeCount, alleleCount))
    
    parsers.foldLeft(success(List[List[VcfValue]]())) { case (ps, p) =>
      ps >> { vs => p ^^ { _ :: vs } }
    } ^^ { _.reverse }
  }
  
  
  def format: Parser[List[Format]] = repsep("[a-zA-Z0-9]+".r, ':') >> { ids =>
    val res = ids.foldLeft(Right(Nil): Either[List[String], List[Format]]) {
      case (Right(fmts), id) =>
        vcfInfo.getTypedMetadata[Format](VcfId(id)) map (f => Right(f :: fmts)) getOrElse (Left(id :: Nil))
        
      case (Left(ids), id) =>
        vcfInfo.getTypedMetadata[Format](VcfId(id)) map (_ => Left(ids)) getOrElse (Left(id :: ids))
    }
    
    res match {
      case Left(ids) => err("Could not find FORMAT descriptions for: %s" format (ids.reverse mkString ", "))
      case Right(fmts) => success(fmts.reverse)
    }
  }
}