package com.slack.sgp.intellij

import java.time.LocalDate

private val LOCAL_DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}\$".toRegex()
private val String.isLocalDate: Boolean
  get() {
    return LOCAL_DATE_REGEX.matches(this)
  }

object ChangelogParser {
  fun readFile(changeLogString: String, previousEntry: LocalDate? = null): ParseResult {
    /*
    date format: yyyy-mm-dd
    */

    if (previousEntry != null && !changeLogString.contains(previousEntry.toString())) {
      return ParseResult(
        changeLogString,
        LocalDate.parse(changeLogString.lines().firstOrNull { it.isLocalDate })
      )
    }
    var previous: LocalDate? = null
    var entryCount = 0
    val changeLogSubstring =
      buildString {
          var currentBlock = StringBuilder()
          for (line in changeLogString.lines()) {
            if (line.isLocalDate) {
              val localDate = LocalDate.parse(line)
              if (localDate == previousEntry) {
                break
              }
              if (previous != null) {
                append(currentBlock.toString())
              }
              currentBlock = StringBuilder()
              if (previous == null) {
                previous = localDate
              }
              entryCount++
            }
            currentBlock.appendLine(line)
          }
          if (entryCount == 0) {
            append(currentBlock.toString())
          }
        }
        .trimEnd()
    return ParseResult(changeLogSubstring.takeIf { it.isNotBlank() }, previous ?: LocalDate.now())
  }
  data class ParseResult(val changeLogString: String?, val latestEntry: LocalDate)
}
