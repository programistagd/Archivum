package com.radeusgd.archivum.datamodel.types

import com.radeusgd.archivum.datamodel.{DMValue, ValidationError}
import com.radeusgd.archivum.persistence.strategies.{Fetch, Insert, Setup}

// TODO
object ImageField extends FieldType {
   override def validate(v: DMValue): List[ValidationError] = ???

   override def makeEmpty: DMValue = ???

   override def tableSetup(path: Seq[String], table: Setup): Unit = ???

   override def tableFetch(path: Seq[String], table: Fetch): DMValue = ???

   override def tableInsert(path: Seq[String], table: Insert, value: DMValue): Unit = ???
}
