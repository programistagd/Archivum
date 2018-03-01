package com.radeusgd.archivum.persistence

import com.radeusgd.archivum.datamodel.{DMStruct, Model}
import com.radeusgd.archivum.persistence.strategies.{FetchImpl, InsertImpl}
import scalikejdbc._

trait Repository {
   type Rid = Long

   def model: Model

   def createRecord(value: DMStruct): Rid

   def fetchRecord(rid: Rid): Option[DMStruct]

   def updateRecord(rid: Rid, newValue: DMStruct): Unit

   def deleteRecord(rid: Rid): Unit

   // TODO using this in processing will be slow, in the future we should extend Repository to handle Streams of records or something similar
   def fetchAllRecords(): Seq[(Rid, DMStruct)]

   def ridSet: RidSetHelper

   def searchRecords(criteria: SearchCriteria): Seq[(Rid, DMStruct)]
}

trait RidSetHelper {
   type Rid = Long // TODO tie this Rid to Repository.Rid

   /*
   Returns amount of records in Repository.
    */
   def count(): Long

   /*
   Returns the index - amount of records before the given one (inclusive).
    */
   def getTemporaryIndex(rid: Rid): Long

   /*
   Intended to be used after delete to find record to display.
   It returns the closest rid before the given one or if there's no such rid, the closest rid after it.
   It returns None only if the Repository is empty.
    */
   def getCloseRid(rid: Rid): Option[Rid]

   /*
   Returns a list of all rids existing in the Repository.
   (Warning: result may be huge.)
    */
   def fetchAllIds(): Seq[Rid]
}

// TODO if there are multiple backends this will diverge into various implementations
class RepositoryImpl(private val _model: Model,
                     private val tableName: String,
                     private val db: DB) extends Repository {

   private def rootType = model.roottype

   private val table: SQLSyntax = DBUtils.rawSql(tableName)

   override def createRecord(value: DMStruct): Rid = {
      assert(rootType.validate(value).isEmpty)
      val ins = new InsertImpl(tableName)
      rootType.tableInsert(Nil, ins, value)
      db.autoCommit({ implicit session => ins.insert(None) })
   }

   private def rsToDM(rs: WrappedResultSet)(implicit session: DBSession): DMStruct = {
      val f = new FetchImpl(rs, tableName)
      rootType.tableFetch(Nil, f)
   }

   override def fetchRecord(rid: Rid): Option[DMStruct] =
      db.readOnly({ implicit session =>
         sql"SELECT * FROM $table WHERE _rid = $rid"
            .map(rs => rsToDM(rs)(session)).single.apply()
      })

   override def updateRecord(rid: Rid, newValue: DMStruct): Unit = {
      assert(rootType.validate(newValue).isEmpty)
      deleteRecord(rid)
      val ins = new InsertImpl(tableName)
      rootType.tableInsert(Nil, ins, newValue)
      val nrid = db.autoCommit({ implicit session => ins.insert(Some(rid)) })
      assert(rid == nrid)
   }

   override def deleteRecord(rid: Rid): Unit = {
      db.autoCommit({ implicit session =>
         sql"DELETE FROM $table WHERE _rid = $rid;".update.apply()
      })
   }

   override def fetchAllRecords(): Seq[(Rid, DMStruct)] = {
      db.readOnly({ implicit session =>
         sql"SELECT * FROM $table"
            .map(rs => (rs.long("_rid"), rsToDM(rs)(session))).list.apply()
      })
   }

   private lazy val ridSetHelper = new RidSetHelperImpl(db, table)

   override def ridSet: RidSetHelper = ridSetHelper

   override def searchRecords(criteria: SearchCriteria): Seq[(Rid, DMStruct)] = ???

   override def model: Model = _model
}

class RidSetHelperImpl(val db: DB, val table: SQLSyntax) extends RidSetHelper {
   override def count(): Rid =
      db.readOnly({ implicit session =>
         sql"SELECT COUNT(_rid) FROM $table"
            .map(rs => rs.long(1)).single.apply()
      }).get // count should always return a value

   override def getTemporaryIndex(rid: Rid): Rid =
      db.readOnly({ implicit session =>
         sql"SELECT COUNT(_rid) FROM $table WHERE _rid <= $rid"
            .map(rs => rs.long(1)).single.apply()
      }).get

   override def getCloseRid(rid: Rid): Option[Rid] =
      db.readOnly({ implicit session =>
         sql"SELECT _rid FROM $table WHERE _rid < $rid ORDER BY _rid DESC LIMIT 1"
            .map(rs => rs.long("_rid")).single.apply()
      }).orElse(
         db.readOnly({ implicit session =>
            sql"SELECT _rid FROM $table WHERE _rid > $rid ORDER BY _rid ASC LIMIT 1"
               .map(rs => rs.long("_rid")).single.apply()
         })
      )

   override def fetchAllIds(): Seq[Rid] =
      db.readOnly({ implicit session =>
         sql"SELECT _rid FROM $table"
            .map(rs => rs.long("_rid")).list.apply()
      })
}