package tech.mlsql.lang.cmd.compile.internal.gc


case class Position(
                     filename: Option[String], // filename, if any
                     offset: Int, // char offset, how many from column
                     line: Int, // line number, starting at 1
                     column: Int // column number, starting at 1 (character count per line)
                   ) {
  def isValid = line > 0

  override def toString: String = {
    val s = filename.getOrElse("input")
    if (!isValid) return s
    s"${s} :${line}:${column}"
  }
}

object Scanner extends Enumeration {
  type TokenType = Value
  val EOF_INT = (-2).toChar
  val EOF = Value("EOF")
  val Ident = Value("Ident")
  val Variable = Value("Variable")
  val Int = Value("Int")
  val Float = Value("Float")
  val Char = Value("Char")
  val String = Value("String")
  val RawString = Value("RawString")
  val Comment = Value("Comment")

  val Lparen = Value("(") // (
  val Lbrack = Value("[") // [
  val Lbrace = Value("{") // {
  val Rparen = Value(")") // )
  val Rbrack = Value("]") // ]
  val Rbrace = Value("}") // }
  val Comma = Value(",") // ,
  val Semi = Value(";") // ;
  val Colon = Value(":") // :
  val Dot = Value(".") // .
  val Assign = Value("=") // .
  val DotDotDot = Value("...") // ...

  // keywords
  val _And = Value("and")
  val _Or = Value("or")
  val _SELECT = Value("select")
  // internal use only
  val skipComment = Value("skipComment")

  val AndAnd = Value("&&") // &&
  val OrOr = Value("||") // ||
  val Not = Value("!") // !
  val Eql = Value("==") // ==
  val Neq = Value("!=") // !=
  val Lss = Value("<") // <
  val Leq = Value("<=") // <=
  val Gtr = Value(">") // >
  val Geq = Value(">=") // >=
  val Add = Value("+") // +
  val Sub = Value("-") // -
  val Or = Value("|") // |
  val Xor = Value("^") // ^
  val Mul = Value("*") // *
  val Div = Value("/") // /
  val Rem = Value("%") // %
  val And = Value("&") // &
  val AndNot = Value("&^") // &^
  val Shl = Value("<<") // <<
  val Shr = Value(">>") // >>

  val KEYWORD_MAP = Map(
    "select" -> Scanner._SELECT,
    "or" -> Scanner._Or,
    "and" -> Scanner._And
  )
}


case class Token(t: Scanner.TokenType,
                 srcPos: Int, srcEnd: Int,
                 line: Int, column: Int,
                 scanner: Scanner)

class Scanner(src: String) {

  var srcChars = src.toCharArray

  // next 当前next指针
  var srcPos = 0
  var lastTokenPos = -1
  // 整个字符串长度
  var srcEnd = src.length


  var line = 1
  var column = 0
  var aheadChar = -1

  var tok: Scanner.TokenType = Scanner.EOF

  private def whiteSpace(s: Char) = {
    s == '\t' || s == '\n' || s == '\r' || s == ' ' || s == ';'
  }

  private def peek: Char = {
    if (srcPos + 1 >= srcEnd) return Scanner.EOF_INT
    val nextChar = srcChars(srcPos + 1)
    nextChar
  }

  private def next: Char = {
    if (srcPos + 1 >= srcEnd) return Scanner.EOF_INT
    val nextChar = srcChars(srcPos + 1)

    srcPos += 1

    nextChar match {
      case '\n' =>
        line += 1
        column = 0
      case _ =>
        column += 1
    }

    nextChar
  }

  private def digits(ch0: Char) = {
    var ch = ch0
    while (ch.isDigit) {
      ch = next
    }
    ch
  }

  private def error(msg: String) = {
    println(s"error: ${msg}")
  }

  private def scanNumber(_ch: Char, _seenDot: Boolean): (Scanner.TokenType, Char) = {
    var seenDot = _seenDot
    var tok = Scanner.EOF
    var ch = _ch
    if (!seenDot) {
      tok = Scanner.Int
      ch = digits(ch)
      if (ch == '.') {
        seenDot = true
      }
    }

    if (seenDot) {
      tok = Scanner.Float
      ch = digits(ch)
    }
    (tok, ch)
  }

  private def scanEscape(quote: Char): Char = {
    var ch = next
    ch match {
      case 'a' | 'b' | 'f' | 'n' | 'r' | 't' | 'v' | '\\' | `quote` =>
        ch = next
      case _ =>
        error("invalid char escape")
    }
    ch
  }

  private def scanString(quote: Char): Int = {
    var ch = next // read character after quote
    var n = 1
    while (peek != quote) {
      if (ch == '\n') {
        error("literal not terminated")
        return n
      }

      if (ch == '\\') {
        ch = scanEscape(quote)
      } else {
        ch = next
      }
      n += 1
    }
    n
  }

  private def isVariable(ch: Char, n: Int): Boolean = {
    if (n == 1) {
      return ch == ':'
    }
    ch.isLetter || ch.isDigit || ch == '_'
  }

  private def scanVariable = {
    var i = 1
    while (isVariable(peek, i)) {
      i += 1
      next
    }
    srcChars(srcPos)
  }

  private def isIdent(ch: Char, n: Int): Boolean = {
    if (n == 1) {
      return ch.isLetter || ch == "_"
    }
    ch.isLetter || ch.isDigit || ch == "_"
  }

  private def scanIdent = {
    next
    var n = 1
    while (isIdent(peek, n)) {
      next
      n += 1
    }
    srcChars(srcPos)
  }

  private def scanComment(_ch: Char): Char = {
    var ch = _ch
    if (ch == '/') {
      ch = next
      while (ch != '\n') {
        ch = next
      }
      return ch
    }
    ch = next
    var stop = false
    while (!stop) {
      val ch0 = ch
      ch = next
      if (ch0 == '*' && ch == '/') {
        ch = next
        stop = true
      }
    }
    ch

  }

  private def scanRawString: Unit = {

    while (peek != '`') {
      next
    }
    srcChars(srcPos)
  }

  private def scanChar: Unit = {
    scanString('\'')
  }

  def scan: Scanner.TokenType = {
    var ch = peek
    while (whiteSpace(peek)) {
      ch = next
    }
    ch = peek
    lastTokenPos = srcPos
    tok = Scanner.EOF

    ch match {
      case s if isVariable(ch, 1) =>
        val tempSrcPos = srcPos
        ch = scanVariable
        tok = Scanner.Variable
        if (srcPos - tempSrcPos == 1) {
          error(": should be variable")
          return Scanner.EOF
        }

      case s if isIdent(s,1) =>
        ch = scanIdent
        tok = Scanner.Ident
        val possibleKeyword = Scanner.KEYWORD_MAP.get(tokenString().toLowerCase())
        if (possibleKeyword.isDefined) {
          tok = possibleKeyword.get
        }
      case s if s.isDigit =>
        val (__tok, __ch) = scanNumber(ch, false)
        tok = __tok
        ch = __ch
      case Scanner.EOF_INT =>
        tok = Scanner.EOF
      case '"' =>
        scanString('"')
        tok = Scanner.String
        ch = next
      case '\'' =>
        scanChar
        tok = Scanner.Char
        ch = next
      case '.' =>
        ch = next
        tok = Scanner.Dot
        val ch0 = peek
        if (ch0.isDigit) {
          val (__tok, __ch) = scanNumber(ch, true)
          tok = __tok
          ch = __ch
        }
      case '/' =>
        ch = next
        tok = Scanner.Div
        val ch0 = peek
        if (ch0 == '/' || ch0 == '*') {
          ch = scanComment(ch)
          tok = Scanner.Comment
        }

      case '`' =>
        scanRawString
        tok = Scanner.RawString
        ch = next
      case '(' =>
        ch = next
        tok = Scanner.Lparen
      case ')' =>
        ch = next
        tok = Scanner.Rparen
      case '[' =>
        ch = next
        tok = Scanner.Lbrack
      case ']' =>
        ch = next
        tok = Scanner.Rbrack
      case '{' =>
        ch = next
        tok = Scanner.Lbrace
      case '}' =>
        ch = next
        tok = Scanner.Rbrace
      case ',' =>
        ch = next
        tok = Scanner.Comma
      case '+' =>
        ch = next
        tok = Scanner.Add
      case '-' =>
        ch = next
        tok = Scanner.Sub
      case '*' =>
        ch = next
        tok = Scanner.Mul
      case '<' =>
        ch = next
        tok = Scanner.Lss
        val ch0 = peek
        if (ch0 == '=') {
          ch = next
          tok = Scanner.Leq
        }
      case '>' =>
        ch = next
        tok = Scanner.Gtr
        val ch0 = peek
        if (ch0 == '=') {
          ch = next
          tok = Scanner.Geq
        }
      case '=' =>
        ch = next
        tok = Scanner.Assign
        val ch0 = peek
        if (ch0 == '=') {
          ch = next
          tok = Scanner.Eql
        }
      case _ =>
        ch = next

    }
    aheadChar = ch
    tok
  }

  def tokenString(): String = {
    val start = lastTokenPos + 1
    (start to srcPos).map(src(_)).mkString("")

  }
}

class Tokenizer {
  def tokenize(str: String): List[Token] = {
    //    val scanner = new Scanner(str)
    //    scanner.scan
    List()
  }
}




