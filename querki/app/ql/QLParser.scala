package ql

import scala.util.parsing.combinator._

import models.system.QLText
import models.Wikitext

sealed abstract class QLTextPart
case class UnQLText(text:String) extends QLTextPart
case class QLName(name:String)
case class QLPhrase(ops:Seq[QLName])
case class QLExp(phrases:Seq[QLPhrase]) extends QLTextPart
case class ParsedQLText(parts:Seq[QLTextPart])

class QLParser(input:QLText) extends RegexParsers {
  val name = """[a-zA-Z][\w- ]*""".r
  val unQLTextRegex = """([^\[]|\[(?!\[))+""".r
  
  def unQLText:Parser[UnQLText] = unQLTextRegex ^^ { UnQLText(_) }
  // TODO: phrase is going to get a *lot* more complex with time:
  def qlPhrase:Parser[QLPhrase] = name ^^ { n => QLPhrase(Seq(QLName(n))) }
  def qlExp:Parser[QLExp] = rep1sep(qlPhrase, "\n") ^^ { QLExp(_) }
  def qlText:Parser[ParsedQLText] = rep(unQLText | "[[" ~> qlExp <~ "]]") ^^ { ParsedQLText(_) }
  
  private def processPhrase(ops:Seq[QLName]):String = {
    val names = ops map ("NAME: " + _.name)
    names.mkString
  }
  
  private def processPhrases(phrases:Seq[QLPhrase]):Seq[String] = {
    phrases map (phrase => processPhrase(phrase.ops))
  }
  
  private def processParseTree(parseTree:ParsedQLText):Wikitext = {
    val strs = parseTree.parts flatMap { 
      _ match {
        case UnQLText(t) => Seq(t)
        case QLExp(phrases) => Seq("[[[") ++ processPhrases(phrases) ++ Seq("]]]")
      }
    }
    Wikitext(strs.mkString)
  }
  
  def process:Wikitext = {
    val parseResult = parseAll(qlText, input.text)
    parseResult match {
      case Success(result, _) => processParseTree(result)
      case Failure(msg, next) => Wikitext("Couldn't parse qlText: " + msg)
      // TODO: we should probably do something more serious in case of Error:
      case Error(msg, next) => Wikitext("ERROR: Couldn't parse qlText: " + msg)
    }
  }
}